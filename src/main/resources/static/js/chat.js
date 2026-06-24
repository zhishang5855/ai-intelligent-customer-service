(function () {
    const state = {
        user: null,
        conversationId: null,
        knowledgeBases: [],
        mode: "plain",
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
            "plainModeBtn",
            "kbModeBtn",
            "knowledgePicker",
            "knowledgeBaseSelect",
            "refreshKnowledgeBtn",
            "conversationIdText",
            "newConversationBtn",
            "conversationTitle",
            "conversationHistory",
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
        els.plainModeBtn.addEventListener("click", function () {
            setMode("plain");
        });
        els.kbModeBtn.addEventListener("click", function () {
            setMode("knowledge");
        });
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
        renderConversationHistory();
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
        els.knowledgeBaseSelect.innerHTML = '<option value="">选择知识库</option><option value="__loading" disabled>加载中...</option>';
        try {
            const response = await window.AicsApi.requestJson("/api/knowledge-bases");
            state.knowledgeBases = response.data || [];
            renderKnowledgeBases();
            showToast("知识库已刷新", "success");
        } catch (error) {
            state.knowledgeBases = [];
            els.knowledgeBaseSelect.innerHTML = '<option value="">选择知识库</option><option value="__failed" disabled>知识库加载失败</option>';
            showToast(error.message, "error");
        }
        updateSubtitle();
    }

    function renderKnowledgeBases() {
        els.knowledgeBaseSelect.innerHTML = '<option value="">选择知识库</option>';
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

        const knowledgeBaseId = state.mode === "knowledge" ? els.knowledgeBaseSelect.value : "";
        const question = els.questionInput.value.trim();
        if (!question) {
            showToast("请输入问题", "error");
            return;
        }
        if (state.mode === "knowledge" && !knowledgeBaseId) {
            showToast("请选择知识库", "error");
            return;
        }

        setBusy(true);
        clearInitialState();
        appendMessage("user", question);
        els.questionInput.value = "";
        const assistantBubble = appendMessage("assistant", "");
        let streamedAnswer = "";

        try {
            const payload = {
                conversationId: state.conversationId,
                userId: state.user.userId,
                question: question
            };
            if (knowledgeBaseId) {
                payload.knowledgeBaseId = Number(knowledgeBaseId);
            }

            await streamChat(payload, {
                onMeta: function (meta) {
                    state.conversationId = meta.conversationId || state.conversationId;
                    updateConversationText();
                    saveConversationHistory(question);
                    renderConversationHistory();
                },
                onSources: function (sources) {
                    renderSources(sources || []);
                },
                onToken: function (token) {
                    streamedAnswer += token;
                    renderMarkdownInto(assistantBubble, streamedAnswer);
                },
                onError: function (message) {
                    renderMarkdownInto(assistantBubble, "请求失败：" + message);
                    showToast(message, "error");
                }
            });
            if (streamedAnswer) {
                showToast("回答已生成", "success");
            }
        } catch (error) {
            renderMarkdownInto(assistantBubble, "请求失败：" + error.message);
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
        if (role === "assistant") {
            bubble.classList.add("markdown-body");
            renderMarkdownInto(bubble, content);
        } else {
            bubble.textContent = content;
        }

        wrapper.appendChild(meta);
        wrapper.appendChild(bubble);
        els.messageList.appendChild(wrapper);
        els.messageList.scrollTop = els.messageList.scrollHeight;
        return bubble;
    }

    async function streamChat(payload, handlers) {
        const headers = {"Content-Type": "application/json"};
        const token = window.AicsAuth.getToken();
        if (token) {
            headers["X-Auth-Token"] = token;
        }

        const response = await fetch("/api/chat/stream", {
            method: "POST",
            headers: headers,
            body: JSON.stringify(payload)
        });

        if (!response.ok || !response.body) {
            let message = "请求失败：" + response.status;
            try {
                const body = await response.json();
                message = body.message || message;
            } catch (error) {
                // Ignore non-JSON error bodies.
            }
            throw new Error(message);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder("utf-8");
        let buffer = "";
        while (true) {
            const result = await reader.read();
            if (result.done) {
                break;
            }
            buffer += decoder.decode(result.value, {stream: true});
            const frames = buffer.split(/\r?\n\r?\n/);
            buffer = frames.pop() || "";
            frames.forEach(function (frame) {
                handleSseFrame(frame, handlers);
            });
        }
        if (buffer.trim()) {
            handleSseFrame(buffer, handlers);
        }
    }

    function handleSseFrame(frame, handlers) {
        const lines = frame.split(/\r?\n/);
        let eventName = "message";
        const dataLines = [];
        lines.forEach(function (line) {
            if (line.startsWith("event:")) {
                eventName = line.slice(6).trim();
            } else if (line.startsWith("data:")) {
                dataLines.push(line.slice(5).replace(/^ /, ""));
            }
        });
        const data = dataLines.join("\n");
        if (eventName === "meta") {
            handlers.onMeta(JSON.parse(data || "{}"));
        } else if (eventName === "sources") {
            handlers.onSources(JSON.parse(data || "[]"));
        } else if (eventName === "token") {
            handlers.onToken(data);
        } else if (eventName === "error") {
            handlers.onError(data || "模型调用失败");
        }
    }

    function renderSources(sources) {
        els.sourcesList.innerHTML = "";
        if (!sources.length) {
            const empty = document.createElement("span");
            empty.className = "empty-sources";
            empty.textContent = state.mode === "knowledge" ? "知识库问答" : "普通对话";
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

            const content = document.createElement("span");
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
        els.conversationTitle.textContent = "智能客服对话";
        els.messageList.innerHTML = '<div class="empty-state"><strong>已新建会话</strong><span>继续输入问题即可开始新的上下文。</span></div>';
        renderSources([]);
        renderConversationHistory();
        showToast("已新建会话", "success");
    }

    function updateConversationText() {
        els.conversationIdText.textContent = state.conversationId ? "会话 ID: " + state.conversationId : "尚未开始";
    }

    function updateSubtitle() {
        const option = els.knowledgeBaseSelect.selectedOptions[0];
        const name = option && option.value ? option.textContent : "";
        els.workspaceSubtitle.textContent = state.mode === "knowledge" && name ? name : "普通对话";
    }

    function setMode(mode) {
        state.mode = mode;
        els.plainModeBtn.classList.toggle("active", mode === "plain");
        els.kbModeBtn.classList.toggle("active", mode === "knowledge");
        els.knowledgePicker.classList.toggle("hidden", mode !== "knowledge");
        renderSources([]);
        updateSubtitle();
    }

    function setBusy(busy) {
        state.busy = busy;
        els.sendBtn.disabled = busy;
        els.refreshKnowledgeBtn.disabled = busy;
        els.questionInput.disabled = busy;
        els.sendBtn.textContent = busy ? "…" : "↑";
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

    function renderMarkdownInto(element, markdown) {
        element.innerHTML = renderMarkdown(markdown || "");
        els.messageList.scrollTop = els.messageList.scrollHeight;
    }

    function renderMarkdown(markdown) {
        const blocks = [];
        let source = escapeHtml(markdown);
        source = source.replace(/```([\s\S]*?)```/g, function (_, code) {
            const token = "\u0000CODE" + blocks.length + "\u0000";
            blocks.push("<pre><code>" + code.trim() + "</code></pre>");
            return token;
        });

        const lines = source.split(/\r?\n/);
        const html = [];
        let listType = "";
        lines.forEach(function (line) {
            const trimmed = line.trim();
            if (!trimmed) {
                closeList();
                return;
            }
            const heading = trimmed.match(/^(#{1,4})\s+(.+)$/);
            if (heading) {
                closeList();
                html.push("<h" + heading[1].length + ">" + renderInlineMarkdown(heading[2]) + "</h" + heading[1].length + ">");
                return;
            }
            const unordered = trimmed.match(/^[-*]\s+(.+)$/);
            if (unordered) {
                openList("ul");
                html.push("<li>" + renderInlineMarkdown(unordered[1]) + "</li>");
                return;
            }
            const ordered = trimmed.match(/^\d+\.\s+(.+)$/);
            if (ordered) {
                openList("ol");
                html.push("<li>" + renderInlineMarkdown(ordered[1]) + "</li>");
                return;
            }
            closeList();
            html.push("<p>" + renderInlineMarkdown(trimmed) + "</p>");
        });
        closeList();

        let rendered = html.join("");
        blocks.forEach(function (block, index) {
            rendered = rendered.replace("\u0000CODE" + index + "\u0000", block);
        });
        return rendered;

        function openList(type) {
            if (listType === type) {
                return;
            }
            closeList();
            listType = type;
            html.push("<" + type + ">");
        }

        function closeList() {
            if (listType) {
                html.push("</" + listType + ">");
                listType = "";
            }
        }
    }

    function renderInlineMarkdown(text) {
        return text
                .replace(/`([^`]+)`/g, "<code>$1</code>")
                .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
                .replace(/\[([^\]]+)]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
    }

    function escapeHtml(value) {
        return String(value)
                .replace(/&/g, "&amp;")
                .replace(/</g, "&lt;")
                .replace(/>/g, "&gt;")
                .replace(/"/g, "&quot;")
                .replace(/'/g, "&#39;");
    }

    function saveConversationHistory(question) {
        if (!state.conversationId) {
            return;
        }
        const items = getConversationHistory().filter(function (item) {
            return item.id !== state.conversationId;
        });
        items.unshift({
            id: state.conversationId,
            title: buildTitle(question),
            mode: state.mode,
            updatedAt: Date.now()
        });
        window.localStorage.setItem(historyKey(), JSON.stringify(items.slice(0, 20)));
        els.conversationTitle.textContent = buildTitle(question);
    }

    function renderConversationHistory() {
        const items = getConversationHistory();
        els.conversationHistory.innerHTML = "";
        if (!items.length) {
            const empty = document.createElement("div");
            empty.className = "history-item";
            empty.innerHTML = "<span>暂无历史对话</span>";
            els.conversationHistory.appendChild(empty);
            return;
        }
        items.forEach(function (item) {
            const row = document.createElement("button");
            row.className = "history-item";
            row.type = "button";
            row.classList.toggle("active", item.id === state.conversationId);
            row.innerHTML = "<span></span><span>...</span>";
            row.firstChild.textContent = item.title || ("会话 " + item.id);
            row.addEventListener("click", function () {
                state.conversationId = item.id;
                els.conversationTitle.textContent = item.title || "智能客服对话";
                updateConversationText();
                showToast("已切换会话", "success");
                renderConversationHistory();
            });
            els.conversationHistory.appendChild(row);
        });
    }

    function getConversationHistory() {
        try {
            return JSON.parse(window.localStorage.getItem(historyKey()) || "[]");
        } catch (error) {
            return [];
        }
    }

    function historyKey() {
        return "aics-conversations-" + state.user.userId;
    }

    function buildTitle(question) {
        const normalized = (question || "").trim();
        if (!normalized) {
            return "智能客服对话";
        }
        return normalized.length > 22 ? normalized.slice(0, 22) + "..." : normalized;
    }

    function displayName(user) {
        return user.nickname || user.username || "当前用户";
    }
})();
