(function () {
    const state = {
        user: null,
        busy: false
    };

    const els = {};

    document.addEventListener("DOMContentLoaded", init);

    function init() {
        state.user = window.AicsAuth.requireLogin();
        if (!state.user) {
            return;
        }

        [
            "knowledgeUserText",
            "knowledgeLogoutBtn",
            "knowledgeForm",
            "knowledgeNameInput",
            "knowledgeDescInput",
            "createKnowledgeBtn",
            "knowledgeMessage",
            "refreshKnowledgeListBtn",
            "knowledgeList",
            "knowledgeListHint"
        ].forEach(function (id) {
            els[id] = document.getElementById(id);
        });

        els.knowledgeUserText.textContent = (state.user.nickname || state.user.username) + "（ID: " + state.user.userId + "）";
        window.AicsAuth.bindLogout(els.knowledgeLogoutBtn);
        els.knowledgeForm.addEventListener("submit", createKnowledgeBase);
        els.refreshKnowledgeListBtn.addEventListener("click", loadKnowledgeBases);
        loadKnowledgeBases();
    }

    async function loadKnowledgeBases() {
        els.knowledgeList.innerHTML = "";
        els.knowledgeListHint.textContent = "加载中";
        try {
            const response = await window.AicsApi.requestJson("/api/knowledge-bases");
            renderList(response.data || []);
        } catch (error) {
            els.knowledgeListHint.textContent = "加载失败";
            showMessage(error.message, "error");
        }
    }

    async function createKnowledgeBase(event) {
        event.preventDefault();
        if (state.busy) {
            return;
        }

        const name = els.knowledgeNameInput.value.trim();
        const description = els.knowledgeDescInput.value.trim();
        if (!name) {
            showMessage("请输入知识库名称", "error");
            els.knowledgeNameInput.focus();
            return;
        }

        setBusy(true);
        try {
            await window.AicsApi.requestJson("/api/knowledge-bases", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({name: name, description: description})
            });
            els.knowledgeNameInput.value = "";
            els.knowledgeDescInput.value = "";
            showMessage("知识库创建成功", "success");
            loadKnowledgeBases();
        } catch (error) {
            showMessage(error.message, "error");
        } finally {
            setBusy(false);
        }
    }

    function renderList(items) {
        els.knowledgeList.innerHTML = "";
        els.knowledgeListHint.textContent = items.length ? ("共 " + items.length + " 个知识库") : "暂无知识库";
        if (!items.length) {
            const empty = document.createElement("div");
            empty.className = "empty-state";
            empty.innerHTML = "<strong>暂无知识库</strong><span>创建后可在客服对话页面选择使用。</span>";
            els.knowledgeList.appendChild(empty);
            return;
        }

        items.forEach(function (item) {
            const card = document.createElement("article");
            card.className = "knowledge-item";

            const title = document.createElement("h3");
            title.textContent = item.name || ("知识库 " + item.id);

            const desc = document.createElement("p");
            desc.textContent = item.description || "暂无描述";

            const meta = document.createElement("span");
            meta.textContent = "ID: " + item.id + " / 状态: " + (item.status || "ACTIVE");

            card.appendChild(title);
            card.appendChild(desc);
            card.appendChild(meta);
            els.knowledgeList.appendChild(card);
        });
    }

    function setBusy(busy) {
        state.busy = busy;
        els.createKnowledgeBtn.disabled = busy;
        els.knowledgeNameInput.disabled = busy;
        els.knowledgeDescInput.disabled = busy;
        els.createKnowledgeBtn.textContent = busy ? "创建中" : "创建知识库";
    }

    function showMessage(message, type) {
        els.knowledgeMessage.textContent = message || "";
        els.knowledgeMessage.className = "form-message " + (type || "");
    }
})();
