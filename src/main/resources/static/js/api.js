(function () {
    async function requestJson(url, options) {
        const requestOptions = options || {};
        const headers = new Headers(requestOptions.headers || {});
        const token = window.AicsAuth && window.AicsAuth.getToken();
        if (token) {
            headers.set("X-Auth-Token", token);
        }

        const response = await fetch(url, Object.assign({}, requestOptions, {headers: headers}));
        let body = null;
        try {
            body = await response.json();
        } catch (error) {
            throw new Error("接口返回不是 JSON");
        }
        if (!response.ok || body.success === false) {
            throw new Error((body && body.message) || ("请求失败：" + response.status));
        }
        return body;
    }

    window.AicsApi = {
        requestJson: requestJson
    };
})();
