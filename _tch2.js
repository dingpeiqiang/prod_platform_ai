const s = '\u6bd4\u0002/';
console.log('a', /[\u4e00-\u9fff]/.test('\u6bd4'));
console.log('b', /[\u4e00-\u9fff]/.test(s));
console.log('c', /\u4e00-\u9fff/.test(s));
console.log('d', s.replace(/([\u4e00-\u9fff])\u0002\//g, '$1</'));
console.log('e', 'x'.replace(/\u4e00/, 'Z'));
