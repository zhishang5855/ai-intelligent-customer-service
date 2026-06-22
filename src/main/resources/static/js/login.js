(function () {
    const els = {};

    document.addEventListener("DOMContentLoaded", init);

    function init() {
        if (window.AicsAuth.getUser()) {
            window.location.href = "/";
            return;
        }

        els.form = document.getElementById("loginForm");
        els.username = document.getElementById("usernameInput");
        els.password = document.getElementById("passwordInput");
        els.button = document.getElementById("loginBtn");
        els.message = document.getElementById("loginMessage");

        els.form.addEventListener("submit", login);
    }

    async function login(event) {
        event.preventDefault();
        const username = els.username.value.trim();
        const password = els.password.value;
        if (!username) {
            showMessage("请输入用户名", "error");
            els.username.focus();
            return;
        }
        if (!password) {
            showMessage("请输入密码", "error");
            els.password.focus();
            return;
        }

        setBusy(true);
        try {
            const response = await window.AicsApi.requestJson("/api/auth/login", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({username: username, password: password})
            });
            window.AicsAuth.saveUser(response.data);
            showMessage("登录成功", "success");
            window.location.href = "/";
        } catch (error) {
            showMessage(error.message, "error");
        } finally {
            setBusy(false);
        }
    }

    function setBusy(busy) {
        els.button.disabled = busy;
        els.username.disabled = busy;
        els.password.disabled = busy;
        els.button.textContent = busy ? "登录中" : "登录";
    }

    function showMessage(message, type) {
        els.message.textContent = message || "";
        els.message.className = "form-message " + (type || "");
    }
})();
