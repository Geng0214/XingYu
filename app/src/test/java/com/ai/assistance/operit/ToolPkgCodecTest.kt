package com.ai.assistance.operit

import com.ai.assistance.operit.core.tools.ToolFailure
import com.ai.assistance.operit.core.tools.packTool.ToolPkgCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ToolPkgCodecTest {
    private val root=File(requireNotNull(System.getProperty("companion.repo")),"app/src/main/assets/toolpkg/time")
    private fun files()=linkedMapOf("manifest.json" to File(root,"manifest.json").readBytes(),"main.js" to File(root,"main.js").readBytes(),"packages/time.js" to File(root,"packages/time.js").readBytes())
    private fun zip(files:Map<String,ByteArray>):ByteArray {
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use {z->files.forEach {(name,bytes)->z.putNextEntry(ZipEntry(name));z.write(bytes);z.closeEntry()}}
        return out.toByteArray()
    }
    private fun reject(bytes:ByteArray,code:String?=null) {
        try {ToolPkgCodec.decode(bytes);fail("Expected package rejection")}
        catch(e:ToolFailure) {if(code!=null)assertEquals(code,e.code)}
    }
    private fun changeManifest(update:(JSONObject)->Unit):ByteArray {
        val files=files();val manifest=JSONObject(files.getValue("manifest.json").decodeToString());update(manifest)
        files["manifest.json"]=manifest.toString().toByteArray();return zip(files)
    }
    @Test fun actualTimePackageHasStableIdentityAndNoExecutablePreview() {
        val files=files();files["main.js"]="throw Error('PREVIEW_MUST_NOT_EXECUTE'); fetch('https://example.invalid/private');".toByteArray()
        val bytes=zip(files);val bundle=ToolPkgCodec.decode(bytes)
        assertEquals("cn.localfirst.companion.time",bundle.packageId)
        assertEquals(setOf("time.get_time","time.format_time"),bundle.tools.map {it.name}.toSet())
        assertEquals(ToolPkgCodec.hash(bytes),bundle.sha256)
        val clone=bundle.archive();clone.fill(0);assertArrayEquals(bytes,bundle.archive())
        assertEquals(2,ToolPkgCodec.decode(bundle.archive()).tools.size)
    }
    @Test fun unknownVersionsAndUnsupportedCapabilitiesAreRejected() {
        reject(changeManifest {it.put("schema_version",2)},"PKG_VERSION")
        reject(changeManifest {it.put("api_version","2.0.0")},"PKG_VERSION")
        for(field in listOf("requires","resources","permissions","capabilities","wasm_modules"))
            reject(changeManifest {it.put(field,org.json.JSONArray().put("network"))},"PKG_CAPABILITY")
        reject(changeManifest {it.put("main","missing.js")})
    }
    @Test fun foreignIdentityAndDefaultsAreOnlyPreviewWarnings() {
        val bundle=ToolPkgCodec.decode(changeManifest {it.put("id","existing-role").put("endpoint","https://example.invalid").put("enabled_by_default",true)})
        assertEquals("cn.localfirst.companion.time",bundle.packageId)
        assertTrue(bundle.warnings.any {it.contains("id")})
        assertTrue(bundle.warnings.any {it.contains("默认启用")})
    }
    @Test fun traversalCaseCollisionsAndAdditionalPayloadsAreRejected() {
        for(path in listOf("../escape.js","/escape.js","a\\escape.js","C:/escape.js","main.JS","payload.apk","payload.zip")) {
            val files=files();files[path]=byteArrayOf(1);reject(zip(files))
        }
        val files=files();files["nested/manifest.json"]=files.getValue("manifest.json");reject(zip(files))
    }
    @Test fun truncatedArchivesAndForgedCrcAreRejected() {
        val bytes=zip(files())
        reject(bytes.copyOf(bytes.size-1));reject(bytes+byteArrayOf(1))
        val central=(0 until bytes.size-3).first {i->bytes[i]==0x50.toByte()&&bytes[i+1]==0x4b.toByte()&&bytes[i+2]==1.toByte()&&bytes[i+3]==2.toByte()}
        val corrupt=bytes.copyOf();corrupt[central+16]=(corrupt[central+16].toInt() xor 1).toByte();reject(corrupt)
        val symlink=bytes.copyOf();symlink[central+40]=0;symlink[central+41]=0xa0.toByte();reject(symlink)
    }
    @Test fun compressedExpandedEntryAndCountBudgetsAreEnforced() {
        reject(ByteArray(ToolPkgCodec.MAX_BYTES+1),"PKG_LIMIT")
        val oversized=files();oversized["large.js"]=ByteArray(256*1024+1){65};reject(zip(oversized),"PKG_LIMIT")
        val expanded=files();repeat(5){expanded["large$it.js"]=ByteArray(220*1024){65}};reject(zip(expanded),"PKG_LIMIT")
        val many=files();repeat(64){many["extra$it.js"]=byteArrayOf(65)};reject(zip(many),"PKG_LIMIT")
    }
    @Test fun invalidUtf8AndDuplicateJsonKeysAreRejected() {
        val bad=files();bad["main.js"]=byteArrayOf(0xc3.toByte(),0x28);reject(zip(bad))
        val duplicate=files();duplicate["manifest.json"]=duplicate.getValue("manifest.json").decodeToString().replaceFirst("{","{\"schema_version\":1,").toByteArray();reject(zip(duplicate))
    }
    @Test fun hjsonMetadataDuplicatesNestingAndEnvironmentAreRejected() {
        for(metadata in listOf("{tools: [], tools: []}","{tools: [], env: [SECRET]}","{tools: [], x:"+"[".repeat(20)+"0"+"]".repeat(20)+"}")) {
            val files=files();files["packages/time.js"]="/* METADATA $metadata */\nexports.test=()=>1;".toByteArray();reject(zip(files))
        }
        val budget=files();budget["packages/time.js"]=("/* METADATA {tools: [], x: \""+"[".repeat(257)+"\"} */").toByteArray();reject(zip(budget))
    }
    @Test fun nestedPackageRootAndHjsonManifestAreSupported() {
        val files=files();val manifest=files.remove("manifest.json")!!
        files["manifest.hjson"]=manifest
        val bundle=ToolPkgCodec.decode(zip(files.mapKeys {"time/"+it.key}))
        assertEquals("main.js",bundle.main);assertTrue(bundle.sources.containsKey("packages/time.js"))
    }
}
