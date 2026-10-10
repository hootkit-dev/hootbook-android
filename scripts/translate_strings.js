const fs = require('fs');
const glob = require('glob');
const xml2js = require('xml2js');
const { translate: bingTranslate } = require('bing-translate-api');
const googleTranslate = require('@iamtraction/google-translate');
const path = require('path');

const resDir = path.join(__dirname, '../app/src/main/res');
const baseStringsPath = path.join(resDir, 'values/strings.xml');

const sleep = ms => new Promise(res => setTimeout(res, ms));

async function translate(text, from, to) {
  try {
    const res = await bingTranslate(text, from, to);
    if (res && res.translation) return res;
  } catch (e) {
    const msg = e.message || '';
    if (!msg.includes('not supported')) throw e;
    console.log(`      Bing unsupported for ${to}, trying Google...`);
  }

  try {
    const res = await googleTranslate(text, { from, to });
    if (res && res.text) return { translation: res.text };
  } catch (e) {
    const msg = e.message || '';
    if (msg.includes('not supported') || msg.toLowerCase().includes('not support')) {
      throw new Error(`Language ${to} is not supported`);
    }
    throw e;
  }
  throw new Error(`Language ${to} is not supported`);
}

async function parseXml(xmlStr) {
    const parser = new xml2js.Parser({ explicitArray: false, preserveChildrenOrder: true });
    return parser.parseStringPromise(xmlStr);
}

function extractEnglishStrings(parsed) {
    const map = {};
    const stringKeys = new Set();
    if (!parsed || !parsed.resources) return { map, stringKeys };
    
    if (parsed.resources.string) {
        const strings = Array.isArray(parsed.resources.string) ? parsed.resources.string : [parsed.resources.string];
        for (const s of strings) {
            if (s && s.$ && s.$.name) {
                map[s.$.name] = s._ || (typeof s === 'string' ? s : '');
                stringKeys.add(s.$.name);
            }
        }
    }
    
    if (parsed.resources.plurals) {
        const plurals = Array.isArray(parsed.resources.plurals) ? parsed.resources.plurals : [parsed.resources.plurals];
        for (const p of plurals) {
            if (p && p.$ && p.$.name && p.item) {
                const items = Array.isArray(p.item) ? p.item : [p.item];
                for (const item of items) {
                    if (item && item.$ && item.$.quantity) {
                        map[`${p.$.name}_${item.$.quantity}`] = item._ || (typeof item === 'string' ? item : '');
                    }
                }
            }
        }
    }
    return { map, stringKeys };
}

function fixFormatSpecifiers(text) {
    if (!text) return text;
    return text
        .replace(/\\'/g, "'")
        .replace(/%\s*(\d+)\s*\$\s*([sd])/g, '%$1$$$2')
        .replace(/%\s*([sd])/g, '%$1')
        .replace(/&amp;/g, '&')
        .replace(/&/g, '&amp;')
        .replace(/'/g, "\\'")
        .replace(/\\+"/g, "\\'");
}

// Convert common Android language codes to Bing's codes if necessary
function mapToBingLangCode(androidCode) {
    let lang = androidCode;
    if (lang === 'zh') return 'zh-Hans';
    if (lang === 'zh-rTW' || lang === 'zh-TW') return 'zh-Hant';
    if (lang === 'in' || lang === 'id') return 'id';
    if (lang === 'val') return 'ca';
    if (lang === 'he' || lang === 'iw') return 'he';
    if (lang === 'tl') return 'fil';
    if (lang === 'no') return 'nb';
    if (lang.includes('-r')) lang = lang.replace('-r', '-');
    return lang;
}

async function main() {
    console.log("Reading base strings...");
    const baseXmlStr = fs.readFileSync(baseStringsPath, 'utf8');
    const baseParsed = await parseXml(baseXmlStr);
    const { map: enMap, stringKeys } = extractEnglishStrings(baseParsed);
    
    const valueDirs = glob.sync('values-*', { cwd: resDir });
    console.log(`Found ${valueDirs.length} language directories.`);
    
    for (const dir of valueDirs) {
        if (dir === 'values-night' || dir === 'values-en' || dir.includes('v31')) continue;
        
        const langCodeMatch = dir.match(/values-([a-zA-Z-]+)/);
        if (!langCodeMatch) continue;
        
        let targetLang = mapToBingLangCode(langCodeMatch[1]);
        const stringsFile = path.join(resDir, dir, 'strings.xml');
        if (!fs.existsSync(stringsFile)) continue;
        
        console.log(`\nProcessing ${dir} (${targetLang})...`);
        const xmlStr = fs.readFileSync(stringsFile, 'utf8');
        let parsed;
        try {
            parsed = await parseXml(xmlStr);
        } catch (e) {
            console.error(`Failed to parse ${stringsFile}: ${e.message}`);
            continue;
        }
        
        let modified = false;
        let unsupported = false;
        const toTranslate = [];
        
        if (!parsed.resources.string) parsed.resources.string = [];
        if (!Array.isArray(parsed.resources.string)) parsed.resources.string = [parsed.resources.string];
        const existingNames = new Set(parsed.resources.string.map(s => s && s.$ && s.$.name));
        for (const key of stringKeys) {
            if (!existingNames.has(key)) {
                parsed.resources.string.push({
                    $: { name: key },
                    _: enMap[key]
                });
            }
        }
        
        if (parsed.resources.string) {
            const strings = Array.isArray(parsed.resources.string) ? parsed.resources.string : [parsed.resources.string];
            for (let i = 0; i < strings.length; i++) {
                const s = strings[i];
                if (s && s.$ && s.$.name && s.$.name !== 'app_name' && s.$.name !== 'hootbook_title') {
                    const name = s.$.name;
                    const enVal = enMap[name];
                    const currentVal = s._ || (typeof s === 'string' ? s : '');
                    
                    if (enVal && currentVal === enVal) {
                        toTranslate.push({ type: 'string', ref: s, text: currentVal });
                    }
                }
            }
        }
        
        if (parsed.resources.plurals) {
            const plurals = Array.isArray(parsed.resources.plurals) ? parsed.resources.plurals : [parsed.resources.plurals];
            for (let i = 0; i < plurals.length; i++) {
                const p = plurals[i];
                if (p && p.$ && p.$.name && p.item) {
                    const items = Array.isArray(p.item) ? p.item : [p.item];
                    for (let j = 0; j < items.length; j++) {
                        const item = items[j];
                        if (item && item.$ && item.$.quantity) {
                            const name = `${p.$.name}_${item.$.quantity}`;
                            const enVal = enMap[name];
                            const currentVal = item._ || (typeof item === 'string' ? item : '');
                            
                            if (enVal && currentVal === enVal) {
                                toTranslate.push({ type: 'plural', ref: item, text: currentVal });
                            }
                        }
                    }
                }
            }
        }
        
        if (toTranslate.length === 0) {
            console.log(`  No placeholders found.`);
            continue;
        }
        
        console.log(`  Translating ${toTranslate.length} placeholders...`);
        
        let i = 0;
        let chunkIndex = 1;
        while (i < toTranslate.length) {
            if (unsupported) break;
            
            let chunk = [];
            let currentLength = 0;
            
            while (i < toTranslate.length) {
                const item = toTranslate[i];
                const additionLength = item.text.length + 10;
                if (chunk.length > 0 && currentLength + additionLength > 900) {
                    break;
                }
                chunk.push(item);
                currentLength += additionLength;
                i++;
            }
            
            process.stdout.write(`    Chunk ${chunkIndex++}... `);
            
            const combined = chunk.map(item => item.text).join('\n ||| \n');
            try {
                const res = await translate(combined, null, targetLang);
                if (!res || !res.translation) throw new Error("Empty translation returned");
                
                let translatedParts = res.translation.split(/\|\s*\|\s*\|/g).map(s => s.trim());
                
                if (translatedParts.length === chunk.length) {
                    for (let j = 0; j < chunk.length; j++) {
                        chunk[j].ref._ = fixFormatSpecifiers(translatedParts[j]);
                    }
                    modified = true;
                    console.log(`OK (Batch, ${chunk.length} items)`);
                } else {
                    console.log(`Mismatch (${chunk.length} vs ${translatedParts.length}). Fallback...`);
                    for (let j = 0; j < chunk.length; j++) {
                        try {
                            const indRes = await translate(chunk[j].text, null, targetLang);
                            if (indRes && indRes.translation) {
                                chunk[j].ref._ = fixFormatSpecifiers(indRes.translation);
                                modified = true;
                            }
                            await sleep(200);
                        } catch (e) {
                            if (e.message.includes('not supported')) {
                                console.log(`\n      Language ${targetLang} is NOT supported by Bing. Skipping...`);
                                unsupported = true;
                                break;
                            }
                            console.error(`\n      Failed single: ${chunk[j].text} -> ${e.message}`);
                        }
                    }
                }
            } catch (e) {
                if (e.message.includes('not supported')) {
                    console.log(`\n      Language ${targetLang} is NOT supported by Bing. Skipping...`);
                    unsupported = true;
                    break;
                }
                console.error(`\n      Failed chunk -> ${e.message}`);
                if (e.message.includes('1000')) {
                     console.log("Length limit error, falling back to 1-by-1...");
                     for (let j = 0; j < chunk.length; j++) {
                        try {
                            const indRes = await translate(chunk[j].text, null, targetLang);
                            if (indRes && indRes.translation) {
                                chunk[j].ref._ = fixFormatSpecifiers(indRes.translation);
                                modified = true;
                            }
                            await sleep(200);
                        } catch (err) {
                            if (err.message.includes('not supported')) {
                                console.log(`\n      Language ${targetLang} is NOT supported by Bing. Skipping...`);
                                unsupported = true;
                                break;
                            }
                            console.error(`\n      Failed single: ${chunk[j].text} -> ${err.message}`);
                        }
                    }
                } else {
                    console.log("RATE LIMIT or ERROR! Wait 30s...");
                    await sleep(30000);
                    i -= chunk.length;
                }
            }
            await sleep(1000);
        }
        
        if (modified) {
            const builder = new xml2js.Builder({
                headless: true,
                renderOpts: { pretty: true, indent: '    ', newline: '\n' },
                cdata: false
            });
            let newXml = builder.buildObject(parsed);
            newXml = '<?xml version="1.0" encoding="utf-8"?>\n' + newXml;
            fs.writeFileSync(stringsFile, newXml);
            console.log(`  Updated ${stringsFile}`);
        }
    }
    console.log("Translation complete!");
}

main().catch(console.error);
