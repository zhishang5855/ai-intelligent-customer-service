(function () {
    const STORAGE_KEY = "aics-user";

    function getUser() {
        const raw = window.localStorage.getItem(STORAGE_KEY);
        if (!raw) {
            return null;
        }
        try {
            return JSON.parse(raw);
        } catch (error) {
            window.localStorage.removeItem(STORAGE_KEY);
            return null;
        }
    }

    function saveUser(user) {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(user));
    }

    function clearUser() {
        window.localStorage.removeItem(STORAGE_KEY);
    }

    function getToken() {
        const user = getUser();
        return user && user.token ? user.token : "";
    }

    function requireLogin() {
        const user = getUser();
        if (!user || !user.userId) {
            window.location.href = "/login.html";
            return null;
        }
        return user;
    }

    function bindLogout(button) {
        if (!button) {
            return;
        }
        button.addEventListener("click", function () {
            clearUser();
            window.location.href = "/login.html";
        });
    }

    window.AicsAuth = {
        getUser: getUser,
        saveUser: saveUser,
        clearUser: clearUser,
        getToken: getToken,
        requireLogin: requireLogin,
        bindLogout: bindLogout
    };
})();
