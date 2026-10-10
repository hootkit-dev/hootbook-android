const fs = require('fs');
const path = require('path');
const xml2js = require('xml2js');

const resDir = '/home/ritesh/Applications/AndroidStudios-Projects/Projects/Hootbook AI Tracker/app/src/main/res';

async function main() {
  const engContent = fs.readFileSync(path.join(resDir, 'values', 'strings.xml'), 'utf8');
  const engParsed = await xml2js.parseStringPromise(engContent, { explicitArray: false });
  
  // Build map: for each format string name, map arg index -> type char
  const argTypeMap = {};
  
  function addFormats(text, name) {
    if (!text) return;
    const re = /%(\d+)\$([dsf])/g;
    let m;
    while ((m = re.exec(text)) !== null) {
      const idx = parseInt(m[1]);
      const type = m[2];
      if (!argTypeMap[name]) argTypeMap[name] = {};
      argTypeMap[name][idx] = type;
    }
  }
  
  // Extract from English strings
  if (engParsed.resources.string) {
    const arr = Array.isArray(engParsed.resources.string) ? engParsed.resources.string : [engParsed.resources.string];
    for (const s of arr) if (s && s.$ && s.$.name) addFormats(s._, s.$.name);
  }
  // Extract from English plurals
  if (engParsed.resources.plurals) {
    const arr = Array.isArray(engParsed.resources.plurals) ? engParsed.resources.plurals : [engParsed.resources.plurals];
    for (const p of arr) if (p && p.$ && p.$.name && p.item) {
      const items = Array.isArray(p.item) ? p.item : [p.item];
      for (const item of items) if (item && item.$) addFormats(item._, p.$.name);
    }
  }
  
  console.log('Format strings:', Object.keys(argTypeMap).length);
  
  const dirs = fs.readdirSync(resDir).filter(d => d.startsWith('values-') && d !== 'values-en');
  let totalFix = 0, fixedFiles = 0;
  
  for (const dir of dirs) {
    const fp = path.join(resDir, dir, 'strings.xml');
    if (!fs.existsSync(fp)) continue;
    let content = fs.readFileSync(fp, 'utf8');
    let changed = false;
    
    for (const [name, argMap] of Object.entries(argTypeMap)) {
      // Build regex to find this string or plural
      const searchStr = 'name="' + name + '">';
      let pos = 0;
      
      while (true) {
        pos = content.indexOf(searchStr, pos);
        if (pos === -1) break;
        
        // Find end of this element (string or plurals)
        const strEnd = content.indexOf('</string>', pos);
        const pluEnd = content.indexOf('</plurals>', pos);
        let tagEnd;
        if (strEnd !== -1 && (pluEnd === -1 || strEnd < pluEnd)) {
          tagEnd = strEnd;
        } else if (pluEnd !== -1) {
          tagEnd = pluEnd;
        } else break;
        
        const closeTag = content.substring(tagEnd, tagEnd + (pluEnd !== -1 && (strEnd === -1 || pluEnd < strEnd) ? 10 : 9));
        const closeLen = closeTag.length;
        const oldBlock = content.substring(pos, tagEnd + closeLen);
        let newBlock = oldBlock;
        
        for (const [idx, type] of Object.entries(argMap)) {
          // Match %N$2 NOT followed by digit, d, or s (which would be valid width+conversion or valid conversion)
          const re = new RegExp('%' + idx + '\\$2(?![ds])', 'g');
          newBlock = newBlock.replace(re, '%' + idx + '$' + type);
        }
        
        if (newBlock !== oldBlock) {
          content = content.substring(0, pos) + newBlock + content.substring(tagEnd + closeLen);
          changed = true;
          totalFix++;
        }
        pos = tagEnd + closeLen;
      }
    }
    
    if (changed) {
      const leftover = content.match(/%\d+\$2(?!\d)(?![ds])/g);
      if (leftover) {
        console.log(dir + ': ' + leftover.length + ' remaining corrupted!');
      }
      fs.writeFileSync(fp, content);
      fixedFiles++;
    }
  }
  
  console.log('Fixed ' + totalFix + ' specs in ' + fixedFiles + ' files');
}
main().catch(console.error);
