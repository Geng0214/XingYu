cmake_minimum_required(VERSION 3.22.1)
project(companion_quickjs LANGUAGES C CXX)
get_filename_component(REPO_ROOT "../../../.." ABSOLUTE BASE_DIR "${CMAKE_CURRENT_LIST_DIR}")
set(QUICKJS_DIR "${REPO_ROOT}/third_party/quickjs")
file(READ "${QUICKJS_DIR}/VERSION" QUICKJS_VERSION)
string(STRIP "${QUICKJS_VERSION}" QUICKJS_VERSION)
if(NOT QUICKJS_VERSION STREQUAL "2026-06-04")
    message(FATAL_ERROR "Unexpected QuickJS source version")
endif()
add_library(quickjsjni SHARED quickjs_jni.cpp
    ${QUICKJS_DIR}/quickjs.c ${QUICKJS_DIR}/cutils.c ${QUICKJS_DIR}/dtoa.c
    ${QUICKJS_DIR}/libregexp.c ${QUICKJS_DIR}/libunicode.c)
# On Windows, QuickJS's VERSION shadows the C++ <version> header in an -I directory.
# Keep its directory out of the C++ search path, as the original Operit bridge did.
file(MAKE_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/generated")
file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/generated/operit_quickjs.h" "#include \"${QUICKJS_DIR}/quickjs.h\"\n")
target_include_directories(quickjsjni PRIVATE "${CMAKE_CURRENT_BINARY_DIR}/generated")
target_compile_definitions(quickjsjni PRIVATE _GNU_SOURCE CONFIG_VERSION="${QUICKJS_VERSION}")
target_compile_options(quickjsjni PRIVATE -O2 -fwrapv -funsigned-char
    "-ffile-prefix-map=${REPO_ROOT}=companion"
    "-fdebug-prefix-map=${REPO_ROOT}=companion")
set_target_properties(quickjsjni PROPERTIES C_STANDARD 11 CXX_STANDARD 17)
target_link_options(quickjsjni PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(quickjsjni m)
