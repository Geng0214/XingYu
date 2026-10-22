import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
const root=path.resolve(import.meta.dirname,'../../..');
const repo=path.join(root,'operit');
const ignored=new Set(['.git','.gradle','.kotlin','.cxx','build','node_modules','.dart_tool','target','operit_deps']);
function walk(dir,skip=false){let files=[];for(const e of fs.readdirSync(dir,{withFileTypes:true})){if(e.isSymbolicLink())continue;const p=path.join(dir,e.name);if(e.isDirectory()){if(!skip||!ignored.has(e.name))files.push(...walk(p,skip));}else files.push(p);}return files;}
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const report={measuredAt:new Date().toISOString(),directories:[],copies:[]};
for(const e of fs.readdirSync(root,{withFileTypes:true})){if(!e.isDirectory())continue;let count=0,bytes=0;for(const p of walk(path.join(root,e.name))){count++;bytes+=fs.statSync(p).size;}report.directories.push({name:e.name,files:count,bytes});}
for(const name of ['operit-m0-a01-1','operit-m0-a01-2','operit-m0-clean-1','operit-m0-clean-2','m0-apply-probe']){
 const dir=path.join(root,name);if(!fs.existsSync(dir))continue;
 const differences=[];
 for(const p of walk(dir,true)){const rel=path.relative(dir,p);const source=path.join(repo,rel);if(!fs.existsSync(source))differences.push({path:rel,kind:'copy-only',bytes:fs.statSync(p).size});else if(hash(p)!==hash(source))differences.push({path:rel,kind:'different',bytes:fs.statSync(p).size});}
 report.copies.push({name,differences});
}
const out=path.join(repo,process.argv.includes('--after')?'evidence/M1/workspace-after.json':'evidence/M1/workspace-before.json');fs.mkdirSync(path.dirname(out),{recursive:true});fs.writeFileSync(out,JSON.stringify(report,null,2));
console.log(JSON.stringify({directories:report.directories,copies:report.copies.map(x=>({name:x.name,differences:x.differences.length,sources:x.differences.filter(d=>/\.(kt|kts|java|dart|sh|py|md)$/.test(d.path))}))},null,2));
