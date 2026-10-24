const mcidInput = document.getElementById('mcidInput');
const codeInput = document.getElementById('codeInput');
const btn       = document.getElementById('btnLogin');
const errEl     = document.getElementById('loginError');

async function doLogin() {
    errEl.textContent = '';
    const mcid = mcidInput.value.trim();
    const code = codeInput.value.trim();
    if (!mcid || !code) {
        errEl.textContent = 'MCID とコードを入力してください';
        return;
    }
    btn.disabled = true;
    try {
        const r = await fetch('/api/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ mcid, code })
        });
        const res = await r.json();
        if (!res.ok) {
            errEl.textContent = res.error === 'invalid mcid or code'
            ? 'MCID またはコードが正しくありません'
            : (res.error || 'ログイン失敗');
            return;
        }
        location.href = '/';
    } catch (e) {
        errEl.textContent = '通信エラー: ' + e;
    } finally {
        btn.disabled = false;
    }
}

btn.addEventListener('click', doLogin);
[mcidInput, codeInput].forEach(el => {
    el.addEventListener('keydown', e => {
        if (e.key === 'Enter') doLogin();
    });
});
