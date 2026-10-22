// Adapted from Operit's QuickJsNativeRuntime bridge. Each invocation owns a new bounded VM.
// The JS standard library, OS modules, dynamic module loader and Java reflection are not linked.
#include <jni.h>
#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <unordered_map>
extern "C" {
#include "operit_quickjs.h"
}

namespace {
using Clock = std::chrono::steady_clock;
constexpr size_t kInputLimit = 2 * 1024 * 1024;
constexpr size_t kOutputLimit = 131072;
struct Vm {
    JSRuntime* runtime = nullptr;
    JSContext* context = nullptr;
    std::atomic_bool cancelled{false};
    std::atomic_bool consumed{false};
    Clock::time_point deadline;
    explicit Vm(int timeout) : deadline(Clock::now() + std::chrono::milliseconds(timeout)) {
        runtime = JS_NewRuntime();
        if (!runtime) throw std::runtime_error("JS_MEMORY");
        JS_SetMemoryLimit(runtime, 32 * 1024 * 1024);
        JS_SetMaxStackSize(runtime, 512 * 1024);
        JS_SetCanBlock(runtime, false);
        JS_SetInterruptHandler(runtime, [](JSRuntime*, void* opaque) {
            auto* vm = static_cast<Vm*>(opaque);
            return vm->cancelled.load() || Clock::now() >= vm->deadline ? 1 : 0;
        }, this);
        context = JS_NewContext(runtime);
        if (!context) { JS_FreeRuntime(runtime); runtime = nullptr; throw std::runtime_error("JS_MEMORY"); }
    }
    ~Vm() { if (context) JS_FreeContext(context); if (runtime) JS_FreeRuntime(runtime); }
    void check() const {
        if (cancelled.load()) throw std::runtime_error("CANCELLED");
        if (Clock::now() >= deadline) throw std::runtime_error("TIMEOUT");
    }
    [[noreturn]] void failed() {
        // Exception values can contain private arguments or hostile getters. Never inspect or log them.
        JSValue exception = JS_GetException(context);
        JS_FreeValue(context, exception);
        check();
        throw std::runtime_error("JS_EXECUTION");
    }
};
struct Value {
    JSContext* ctx;
    JSValue value;
    Value(JSContext* ctx, JSValue value) : ctx(ctx), value(value) {}
    ~Value() { JS_FreeValue(ctx, value); }
    void reset(JSValue next) { JS_FreeValue(ctx, value); value = next; }
};
std::mutex registryMutex;
std::unordered_map<jlong, std::shared_ptr<Vm>> registry;
jlong nextId = 1;
std::shared_ptr<Vm> find(jlong id) {
    std::lock_guard<std::mutex> lock(registryMutex);
    auto it = registry.find(id);
    if (it == registry.end()) throw std::runtime_error("JS_CLOSED");
    return it->second;
}
void fail(JNIEnv* env, const char* code) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), code);
}
std::string input(JNIEnv* env, jbyteArray bytes) {
    if (!bytes) throw std::runtime_error("ARGUMENTS");
    auto size = env->GetArrayLength(bytes);
    if (size <= 0 || static_cast<size_t>(size) > kInputLimit) throw std::runtime_error("JS_INPUT_LIMIT");
    std::string result(size, '\0');
    env->GetByteArrayRegion(bytes, 0, size, reinterpret_cast<jbyte*>(result.data()));
    if (env->ExceptionCheck()) throw std::runtime_error("JS_MEMORY");
    return result;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_ai_assistance_operit_core_tools_javascript_QuickJsNativeBridge_create(JNIEnv* env, jclass, jint timeout) {
    try {
        if (timeout < 100 || timeout > 30000) throw std::runtime_error("ARGUMENTS");
        std::lock_guard<std::mutex> lock(registryMutex);
        if (registry.size() >= 4) throw std::runtime_error("BUSY");
        auto vm = std::make_shared<Vm>(timeout);
        jlong id = nextId++;
        registry.emplace(id, std::move(vm));
        return id;
    } catch (const std::exception& e) { fail(env, e.what()); return 0; }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_ai_assistance_operit_core_tools_javascript_QuickJsNativeBridge_evaluate(JNIEnv* env, jclass, jlong id, jbyteArray script) {
    try {
        auto vm = find(id);
        if (vm->consumed.exchange(true)) throw std::runtime_error("JS_REUSED");
        vm->check();
        const auto source = input(env, script);
        JS_UpdateStackTop(vm->runtime);
        Value result(vm->context, JS_Eval(vm->context, source.data(), source.size(), "toolpkg.js", JS_EVAL_TYPE_GLOBAL));
        if (JS_IsException(result.value)) vm->failed();
        int jobs = 0;
        while (JS_PromiseState(vm->context, result.value) == JS_PROMISE_PENDING) {
            vm->check();
            if (++jobs > 4096) throw std::runtime_error("JS_JOB_LIMIT");
            JSContext* jobContext = nullptr;
            int count = JS_ExecutePendingJob(vm->runtime, &jobContext);
            if (count < 0) vm->failed();
            if (count == 0) throw std::runtime_error("JS_PENDING");
        }
        vm->check();
        auto state = JS_PromiseState(vm->context, result.value);
        if (state == JS_PROMISE_REJECTED) throw std::runtime_error("JS_EXECUTION");
        if (state == JS_PROMISE_FULFILLED) result.reset(JS_PromiseResult(vm->context, result.value));
        if (JS_IsUndefined(result.value)) throw std::runtime_error("JS_RESULT");
        Value json(vm->context, JS_JSONStringify(vm->context, result.value, JS_UNDEFINED, JS_UNDEFINED));
        if (JS_IsException(json.value)) vm->failed();
        if (JS_IsUndefined(json.value)) throw std::runtime_error("JS_RESULT");
        size_t size = 0;
        const char* chars = JS_ToCStringLen(vm->context, &size, json.value);
        if (!chars) vm->failed();
        if (size > kOutputLimit) { JS_FreeCString(vm->context, chars); throw std::runtime_error("OUTPUT_LIMIT"); }
        // Byte arrays use actual UTF-8, unlike JNI modified UTF-8 strings which corrupt emoji/NUL.
        jbyteArray output = env->NewByteArray(static_cast<jsize>(size));
        if (output) env->SetByteArrayRegion(output, 0, static_cast<jsize>(size), reinterpret_cast<const jbyte*>(chars));
        JS_FreeCString(vm->context, chars);
        vm->check();
        return output;
    } catch (const std::exception& e) { fail(env, e.what()); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_operit_core_tools_javascript_QuickJsNativeBridge_interrupt(JNIEnv*, jclass, jlong id) {
    std::lock_guard<std::mutex> lock(registryMutex);
    auto it = registry.find(id);
    if (it != registry.end()) it->second->cancelled.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ai_assistance_operit_core_tools_javascript_QuickJsNativeBridge_close(JNIEnv*, jclass, jlong id) {
    std::shared_ptr<Vm> closing;
    {
        std::lock_guard<std::mutex> lock(registryMutex);
        auto it = registry.find(id);
        if (it != registry.end()) {
            closing = it->second;
            closing->cancelled.store(true);
            registry.erase(it);
        }
    }
    // An evaluation holds its own shared_ptr. Concurrent cancellation/close cannot free a live VM.
}
