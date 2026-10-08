const path = require('path')
const fs = require('fs')
const { JSDOM } = require(process.env.TEMP + '\\jsdomtest\\node_modules\\jsdom')

const dir = path.join(__dirname, 'frontend', 'public', 'ops-web')
const html = fs.readFileSync(path.join(dir, 'config-workbench.html'), 'utf8')

const dom = new JSDOM(html, {
  runScripts: 'dangerously',
  resources: 'usable',
  url: 'http://localhost/ops-web/config-workbench.html',
  pretendToBeVisual: true,
})
const { window } = dom

function load(rel) {
  const code = fs.readFileSync(path.join(dir, 'js', rel), 'utf8')
  const s = window.document.createElement('script')
  s.textContent = code
  window.document.head.appendChild(s)
}

window.addEventListener('error', (e) => console.log('PAGE ERROR:', e.error && e.error.stack || e.message))

// vendor vue/echarts + stage map sync already maybe; load in order
load('vendor-vue.js')
load('vendor-echarts.js')
load('elmessage.js')
load('data-presets.js')
load('app-config-workbench.js')
load('ops-view.js')
load('wb-context.js')
load('wb-products.js')
load('app-main.js')

setTimeout(() => {
  const doc = window.document
  const rows = doc.querySelectorAll('.form-row')
  let found = false
  rows.forEach((r) => {
    const label = r.querySelector('.label-text')
    if (label && label.textContent.includes('套餐固定费')) {
      found = true
      const input = r.querySelector('input,select,textarea')
      console.log('FOUND 套餐固定费 row. input value =', JSON.stringify(input && input.value), 'type=', input && input.type)
    }
  })
  if (!found) console.log('套餐固定费 row NOT rendered')
  console.log('activeTab config content present:', !!doc.querySelector('.tab-content'))
}, 3000)
