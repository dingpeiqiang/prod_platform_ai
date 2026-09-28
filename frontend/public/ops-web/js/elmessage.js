/* ---- ElMessage 杞婚噺瀹炵幇 ---- */
(function(global){
  let timer = null;
  function show(type, msg){
    let el = document.querySelector('.msg-toast');
    if (!el) { el = document.createElement('div'); el.className='msg-toast'; document.body.appendChild(el); }
    el.className = 'msg-toast ' + type;
    el.textContent = msg;
    requestAnimationFrame(()=>{ el.style.opacity = '1'; });
    if (timer) clearTimeout(timer);
    timer = setTimeout(()=>{ el.style.opacity = '0'; }, 2000);
  }
  global.ElMessage = {
    success: (m)=>show('success', m),
    error: (m)=>show('error', m),
    info: (m)=>show('info', m),
    warning: (m)=>show('info', m),
  };
})(window);
