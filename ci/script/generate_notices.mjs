import fs from 'node:fs';
import path from 'node:path';
const repo=path.resolve(import.meta.dirname,'../..');
const cache=path.resolve(repo,'.local/.gradle-m0/caches/modules-2/files-2.1');
const stage=process.argv[2]??'E5_AUDIO';
if(!/^[A-Z0-9_-]+$/.test(stage))throw Error('Invalid evidence stage');
const coordinates=fs.readFileSync(path.join(repo,`evidence/${stage}/runtime-coordinates.txt`),'utf8').trim().split(/\r?\n/);
const rows=[];
function descendants(dir){if(!fs.existsSync(dir))return [];return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?descendants(path.join(dir,e.name)):[path.join(dir,e.name)]);}
const clean=s=>s.replace(/<[^>]*>/g,'').replaceAll('&amp;','&').trim();
function readLicense(xml) {
 const segment=xml.match(/<licenses>([\s\S]*?)<\/licenses>/)?.[1];
 if(segment)return [...segment.matchAll(/<license>([\s\S]*?)<\/license>/g)].map(x=>({name:clean(x[1].match(/<name>([\s\S]*?)<\/name>/)?.[1]??''),url:clean(x[1].match(/<url>([\s\S]*?)<\/url>/)?.[1]??'')}));
 const parent=xml.match(/<parent>([\s\S]*?)<\/parent>/)?.[1];
 if(!parent)throw Error('No license declaration or Maven parent');
 const ids=['groupId','artifactId','version'].map(tag=>clean(parent.match(new RegExp(`<${tag}>([\\s\\S]*?)</${tag}>`))[1]));
 const pom=descendants(path.join(cache,...ids)).find(p=>p.endsWith('.pom'));
 if(!pom)throw Error(`Missing parent POM ${ids.join(':')}`);
 return readLicense(fs.readFileSync(pom,'utf8'));
}
for(const coordinate of coordinates){const [group,name,version]=coordinate.split(':');const poms=descendants(path.join(cache,group,name,version)).filter(p=>p.endsWith('.pom'));
 if(poms.length!==1)throw Error(`Expected one resolved POM: ${coordinate}`);
 const xml=fs.readFileSync(poms[0],'utf8');const licenses=readLicense(xml);
 if(!licenses.length)throw Error(`Missing POM license: ${coordinate}`);
 rows.push({coordinate,licenses,source:`https://repo.maven.apache.org/maven2/${group.replaceAll('.','/')}/${name}/${version}/${name}-${version}.pom`});
}
let text='# 星语：第三方声明\n\n本应用基于 Operit 修改，主代码采用 LGPL-3.0-only。上游：https://github.com/AAswordman/Operit 。完整对应源码与构建说明随交付提供。\n\n以下清单来自当前 releaseRuntimeClasspath，不沿用历史完整版的依赖清单。POM 名称仅表示上游声明，不将清单生成视为法律审查。\n\n';
text+='原生脚本运行时 QuickJS 2026-06-04，MIT，源码：https://bellard.org/quickjs/ 。版本和源码哈希固定于 third_party/quickjs/source-lock.json；未链接 std/os 或动态模块加载器。\n\n';
text+='家园场景使用 Godot 4.7.2（MIT），作物阶段与图集资源改编自 Tall Tales Games 的 2D Farming Game（MIT，https://github.com/gadget-hq/2d-farming-game）。美术使用 Kenney Tiny Town / Tiny Farm / Tiny Dungeon（CC0-1.0）；图集来源与哈希见 game/home/art/sources.json。未打包上游 Sprout Lands 素材。\n\n';
text+='天气由 Open-Meteo 提供（https://open-meteo.com/），地点检索数据基于 GeoNames；天气与地点数据按 CC BY 4.0 提供（https://creativecommons.org/licenses/by/4.0/）。应用不打包这些数据，只在用户确认城市和服务后请求。免费 Open-Meteo 接口仅用于非商业开发与个人测试，商业使用需另行确认服务方案。\n\n';
text+=rows.map(r=>`- ${r.coordinate}: ${r.licenses.map(l=>`${l.name} (${l.url})`).join('; ')}`).join('\n');
text+='\n\n## 许可正文\n\n';
for(const name of ['gpl-3.0.txt','lgpl-3.0.txt','apache-2.0.txt']){
 const p=path.join(repo,'third_party/licenses',name);if(!fs.existsSync(p))throw Error(`Missing license text ${name}`);text+=`\n### ${name}\n\n`+fs.readFileSync(p,'utf8')+'\n';
}
const componentLicenses=['sqlcipher.txt','sqlcipher-android.txt','openssl.txt'];
if(coordinates.some(c=>c.startsWith('io.modelcontextprotocol:')))componentLicenses.push('mcp-kotlin-sdk.txt');
if(coordinates.some(c=>c.startsWith('org.slf4j:')))componentLicenses.push('slf4j.txt');
if(coordinates.some(c=>c.startsWith('org.hjson:')))componentLicenses.push('hjson.txt');
for(const name of componentLicenses){
 const p=path.join(repo,'third_party/licenses',name);if(!fs.existsSync(p))throw Error(`Missing native license ${name}`);text+=`\n### ${name}\n\n`+fs.readFileSync(p,'utf8')+'\n';
}
text+='\n### QuickJS\n\n'+fs.readFileSync(path.join(repo,'third_party/quickjs/LICENSE'),'utf8');
for(const name of ['tall-tales-farming-mit.txt','kenney-tiny-town.txt','kenney-tiny-farm.txt','kenney-tiny-dungeon.txt','godot-4.7.2.txt']){
 text+=`\n### ${name}\n\n`+fs.readFileSync(path.join(repo,'third_party/licenses',name),'utf8');
}
text=text.trimEnd()+'\n';
fs.writeFileSync(path.join(repo,'THIRD-PARTY-NOTICES.md'),text);
fs.mkdirSync(path.join(repo,'app/src/main/assets'),{recursive:true});
fs.writeFileSync(path.join(repo,'app/src/main/assets/THIRD-PARTY-NOTICES.txt'),text);
fs.writeFileSync(path.join(repo,`evidence/${stage}/runtime-licenses.json`),JSON.stringify(rows,null,2));
console.log(`Generated notices for ${rows.length} runtime coordinates.`);
