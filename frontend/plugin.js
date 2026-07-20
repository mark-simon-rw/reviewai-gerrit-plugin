
console.log("window.Gerrit =", window.Gerrit);
console.log("Gerrit =", typeof Gerrit);

Gerrit.install(plugin => {
  console.error('[AI Code Review] Plugin JS loaded');

  plugin.hook('change-view-integration').onAttached(hookEl => {
    console.error('[AI Code Review] Hook attached');

    const btn = document.createElement('button');
    btn.textContent = 'AI Review';
    btn.style.cssText = [
      'display:inline-block',
      'margin:8px 0',
      'padding:8px 20px',
      'background:#1976d2',
      'color:#fff',
      'border:none',
      'border-radius:4px',
      'font-size:14px',
      'font-family:inherit',
      'cursor:pointer',
      'font-weight:500',
    ].join(';');

    btn.onmouseover = () => (btn.style.background = '#1565c0');
    btn.onmouseout = () => (btn.style.background = '#1976d2');

    btn.onclick = () => {
      const match = window.location.pathname.match(/\/\+\/(\d+)/);
      if (!match) {
        console.error('[AI Code Review] Cannot find change number in URL');
        return;
      }

      const changeNum = match[1];
      btn.disabled = true;
      btn.textContent = 'Reviewing...';
      btn.style.background = '#90a4ae';

      plugin.restApi()
        .post('/changes/' + changeNum + '/ai-review', {})
        .then(() => {
          console.log('[AI Code Review] Review triggered for change', changeNum);
          btn.textContent = 'Review Started';
          btn.style.background = '#388e3c';
          setTimeout(() => {
            btn.textContent = 'AI Review';
            btn.style.background = '#1976d2';
            btn.disabled = false;
          }, 4000);
        })
        .catch(err => {
          console.error('[AI Code Review] Error:', err);
          btn.textContent = 'AI Review (failed)';
          btn.style.background = '#d32f2f';
          btn.disabled = false;
        });
    };

    hookEl.appendChild(btn);
  });
});