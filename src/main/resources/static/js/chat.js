(function () {
    const state = {
        user: null,
        conversationId: null,
        knowledgeBases: [],
        busy: false
    };

    const els = {};

    document.addEventListener("DOMContentLoaded", init);

    function init() {
        state.user = window.AicsAuth.requireLogin();
        if (!state.user) {
            return;
        }

        mapElements();
        els.currentUserText.textContent = displayName(state.user);
        window.AicsAuth.bindLogout(els.logoutBtn);
        bindEvents();
        checkHealth();
        loadKnowledgeBases();
    }

    function mapElements() {
        [
            "currentUserText",
            "logoutBtn",
            "healthDot",
            "healthText",
            "knowledgeBaseSelect",
            "refreshKnowledgeBtn",
            "conversationIdText",
            "newConversationBtn",
            "workspaceSubtitle",
            "toast",
            "messageList",
            "chatForm",
            "questionInput",
            "sendBtn",
            "sourcesList"
        ].forEach(function (id) {
            els[id] = document.getElementById(id);
        });
    }

    function bindEvents() {
        els.refreshKnowledgeBtn.addEventListener("click", loadKnowledgeBases);
        els.newConversationBtn.addEventListener("click", resetConversation);
        els.knowledgeBaseSelect.addEventListener("change", function () {
            updateSubtitle();
            renderSources([]);
        });
        els.chatForm.addEventListener("submit", sendQuestion);
        els.questionInput.addEventListener("keydown", function (event) {
            if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
                els.chatForm.requestSubmit();
            }
        });
    }

    async function checkHealth() {
        try {
            await window.AicsApi.requestJson("/api/health");
            setHealth(true, "后端在线");
        } catch (error) {
            setHealth(false, "后端不可用");
            showToast(error.message, "error");
        }
    }

    async function loadKnowledgeBases() {
        els.knowledgeBaseSelect.innerHTML = '<option value="">普通对话，不使用知识库</option><option value="__loading" disabled>加载中...</option>';
        try {
            const response = await window.AicsApi.requestJson("/api/knowledge-bases");
            state.knowledgeBases = response.data || [];
            renderKnowledgeBases();
            showToast("知识库已刷新", "success");
        } catch (error) {
            state.knowledgeBases = [];
            els.knowledgeBaseSelect.innerHTML = '<option value="">普通对话，不使用知识库</option><option value="__failed" disabled>知识库加载失败</option>';
            showToast(error.message, "error");
        }
        updateSubtitle();
    }

    function renderKnowledgeBases() {
        els.knowledgeBaseSelect.innerHTML = '<option value="">普通对话，不使用知识库</option>';
        if (!state.knowledgeBases.length) {
            const emptyOption = document.createElement("option");
            emptyOption.value = "__empty";
            emptyOption.disabled = true;
            emptyOption.textContent = "暂无知识库";
            els.knowledgeBaseSelect.appendChild(emptyOption);
            return;
        }

        state.knowledgeBases.forEach(function (kb) {
            const option = document.createElement("option");
            option.value = kb.id;
            option.textContent = kb.name || ("知识库 " + kb.id);
            els.knowledgeBaseSelect.appendChild(option);
        });
    }

    async function sendQuestion(event) {
        event.preventDefault();
        if (state.busy) {
            return;
        }

        const knowledgeBaseId = els.knowledgeBaseSelect.value;
        const question = els.questionInput.value.trim();
        if (!question) {
            showToast("请输入问题", "error");
            return;
        }

        setBusy(true);
        clearInitialState();
        appendMessage("user", question);
        els.questionInput.value = "";

        try {
            const payload = {
                conversationId: state.conversationId,
                userId: state.user.userId,
                question: question
            };
            if (knowledgeBaseId) {
                payload.knowledgeBaseId = Number(knowledgeBaseId);
            }

            const response = await window.AicsApi.requestJson("/api/chat", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(payload)
            });
            const data = response.data || {};
            state.conversationId = data.conversationId || state.conversationId;
            updateConversationText();
            appendMessage("assistant", data.answer || "未返回回答");
            renderSources(data.sources || []);
            showToast("回答已生成", "success");
        } catch (error) {
            appendMessage("assistant", "请求失败：" + error.message);
            showToast(error.message, "error");
        } finally {
            setBusy(false);
        }
    }

    function appendMessage(role, content) {
        const wrapper = document.createElement("article");
        wrapper.className = "message " + role;

        const meta = document.createElement("div");
        meta.className = "message-meta";
        meta.textContent = role === "user" ? "用户" : "智能客服";

        const bubble = document.createElement("div");
        bubble.className = "message-bubble";
        bubble.textContent = content;

        wrapper.appendChild(meta);
        wrapper.appendChild(bubble);
        els.messageList.appendChild(wrapper);
        els.messageList.scrollTop = els.messageList.scrollHeight;
    }

    function renderSources(sources) {
        els.sourcesList.innerHTML = "";
        if (!sources.length) {
            const empty = document.createElement("div");
            empty.className = "empty-sources";
            empty.textContent = els.knowledgeBaseSelect.value ? "暂无来源片段" : "普通对话无来源片段";
            els.sourcesList.appendChild(empty);
            return;
        }

        sources.forEach(function (source) {
            const card = document.createElement("article");
            card.className = "source-card";

            const header = document.createElement("div");
            header.className = "source-card-header";

            const name = document.createElement("span");
            name.textContent = source.documentName || ("文档 " + source.documentId);

            const score = document.createElement("span");
            score.className = "score";
            score.textContent = formatScore(source.score);

            const content = document.createElement("div");
            content.className = "source-content";
            content.textContent = source.content || "";

            header.appendChild(name);
            header.appendChild(score);
            card.appendChild(header);
            card.appendChild(content);
            els.sourcesList.appendChild(card);
        });
    }

    function formatScore(score) {
        const value = Number(score);
        if (Number.isNaN(value)) {
            return "-";
        }
        return Math.round(value * 100) + "%";
    }

    function resetConversation() {
        state.conversationId = null;
        updateConversationText();
        els.messageList.innerHTML = '<div class="empty-state"><strong>已新建会话</strong><span>继续输入问题即可开始新的上下文。</span></div>';
        renderSources([]);
        showToast("已新建会话", "success");
    }

    function updateConversationText() {
        els.conversationIdText.textContent = state.conversationId ? String(state.conversationId) : "尚未开始";
    }

    function updateSubtitle() {
        const option = els.knowledgeBaseSelect.selectedOptions[0];
        const name = option && option.value ? option.textContent : "";
        els.workspaceSubtitle.textContent = name ? "当前模式：知识库问答 - " + name : "当前模式：普通对话";
    }

    function setBusy(busy) {
        state.busy = busy;
        els.sendBtn.disabled = busy;
        els.refreshKnowledgeBtn.disabled = busy;
        els.questionInput.disabled = busy;
        els.sendBtn.textContent = busy ? "生成中" : "发送";
    }

    function setHealth(online, text) {
        els.healthDot.classList.toggle("online", online);
        els.healthDot.classList.toggle("offline", !online);
        els.healthText.textContent = text;
    }

    function showToast(message, type) {
        els.toast.textContent = message || "";
        els.toast.className = "toast " + (type || "");
        if (message) {
            window.clearTimeout(showToast.timer);
            showToast.timer = window.setTimeout(function () {
                els.toast.textContent = "";
                els.toast.className = "toast";
            }, 4200);
        }
    }

    function clearInitialState() {
        const empty = els.messageList.querySelector(".empty-state");
        if (empty) {
            empty.remove();
        }
    }

    function displayName(user) {
        return (user.nickname || user.username || "当前用户") + "（ID: " + user.userId + "）";
    }
})();
