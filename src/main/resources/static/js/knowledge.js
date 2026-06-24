(function () {
    const state = {
        user: null,
        busy: false,
        uploadBusy: false,
        knowledgeBases: [],
        selectedKnowledgeBaseId: null
    };

    const MAX_UPLOAD_SIZE = 20 * 1024 * 1024;
    const SUPPORTED_EXTENSIONS = ["txt", "md", "markdown", "pdf", "docx"];

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
            "knowledgeListHint",
            "selectedKnowledgeTitle",
            "selectedKnowledgeMeta",
            "refreshDocumentListBtn",
            "documentUploadForm",
            "documentFileInput",
            "documentFileText",
            "uploadDocumentBtn",
            "documentMessage",
            "documentListHint",
            "documentList"
        ].forEach(function (id) {
            els[id] = document.getElementById(id);
        });

        els.knowledgeUserText.textContent = (state.user.nickname || state.user.username) + "（ID: " + state.user.userId + "）";
        window.AicsAuth.bindLogout(els.knowledgeLogoutBtn);
        els.knowledgeForm.addEventListener("submit", createKnowledgeBase);
        els.refreshKnowledgeListBtn.addEventListener("click", loadKnowledgeBases);
        els.refreshDocumentListBtn.addEventListener("click", loadDocuments);
        els.documentUploadForm.addEventListener("submit", uploadDocument);
        els.documentFileInput.addEventListener("change", updateSelectedFileText);
        loadKnowledgeBases();
    }

    async function loadKnowledgeBases() {
        els.knowledgeList.innerHTML = "";
        els.knowledgeListHint.textContent = "加载中";
        try {
            const response = await window.AicsApi.requestJson("/api/knowledge-bases");
            state.knowledgeBases = response.data || [];
            renderList(state.knowledgeBases);
            syncSelectedKnowledgeBase();
            showMessage("", "");
        } catch (error) {
            state.knowledgeBases = [];
            els.knowledgeListHint.textContent = "加载失败";
            showMessage(error.message, "error");
            renderDocumentEmpty("知识库加载失败");
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
            const response = await window.AicsApi.requestJson("/api/knowledge-bases", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({name: name, description: description})
            });
            els.knowledgeNameInput.value = "";
            els.knowledgeDescInput.value = "";
            if (response.data && response.data.id) {
                state.selectedKnowledgeBaseId = response.data.id;
            }
            await loadKnowledgeBases();
            showMessage("知识库创建成功", "success");
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
            card.classList.toggle("active", item.id === state.selectedKnowledgeBaseId);

            const title = document.createElement("h3");
            title.textContent = item.name || ("知识库 " + item.id);

            const desc = document.createElement("p");
            desc.textContent = item.description || "暂无描述";

            const meta = document.createElement("span");
            meta.textContent = "ID: " + item.id + " / 状态: " + (item.status || "ACTIVE");

            const actions = document.createElement("div");
            actions.className = "knowledge-item-actions";

            const selectBtn = document.createElement("button");
            selectBtn.className = "secondary-btn compact-btn";
            selectBtn.type = "button";
            selectBtn.textContent = item.id === state.selectedKnowledgeBaseId ? "已选择" : "管理文档";
            selectBtn.addEventListener("click", function () {
                selectKnowledgeBase(item.id);
            });
            actions.appendChild(selectBtn);

            card.appendChild(title);
            card.appendChild(desc);
            card.appendChild(meta);
            card.appendChild(actions);
            els.knowledgeList.appendChild(card);
        });
    }

    function syncSelectedKnowledgeBase() {
        if (state.selectedKnowledgeBaseId) {
            const exists = state.knowledgeBases.some(function (item) {
                return item.id === state.selectedKnowledgeBaseId;
            });
            if (exists) {
                renderSelectedKnowledgeBase();
                loadDocuments();
                return;
            }
        }
        state.selectedKnowledgeBaseId = null;
        renderSelectedKnowledgeBase();
        renderDocumentEmpty(state.knowledgeBases.length ? "请选择一个知识库" : "暂无知识库");
    }

    function selectKnowledgeBase(knowledgeBaseId) {
        state.selectedKnowledgeBaseId = knowledgeBaseId;
        renderList(state.knowledgeBases);
        renderSelectedKnowledgeBase();
        loadDocuments();
    }

    function selectedKnowledgeBase() {
        return state.knowledgeBases.find(function (item) {
            return item.id === state.selectedKnowledgeBaseId;
        });
    }

    function renderSelectedKnowledgeBase() {
        const selected = selectedKnowledgeBase();
        const hasSelection = !!selected;
        els.selectedKnowledgeTitle.textContent = selected ? selected.name : "文档管理";
        els.selectedKnowledgeMeta.textContent = selected
                ? "知识库 ID: " + selected.id + " / 状态: " + (selected.status || "ACTIVE")
                : "请选择一个知识库";
        els.refreshDocumentListBtn.disabled = !hasSelection || state.uploadBusy;
        els.documentFileInput.disabled = !hasSelection || state.uploadBusy;
        els.uploadDocumentBtn.disabled = !hasSelection || state.uploadBusy;
        if (!hasSelection) {
            els.documentFileInput.value = "";
            els.documentFileText.textContent = "选择 TXT / MD / PDF / DOCX 文件，最大 20MB";
        }
    }

    async function loadDocuments() {
        if (!state.selectedKnowledgeBaseId) {
            renderDocumentEmpty("请选择一个知识库");
            return;
        }

        els.documentList.innerHTML = "";
        els.documentListHint.textContent = "文档加载中";
        try {
            const response = await window.AicsApi.requestJson(
                    "/api/knowledge-bases/" + state.selectedKnowledgeBaseId + "/documents"
            );
            renderDocuments(response.data || []);
        } catch (error) {
            els.documentListHint.textContent = "文档加载失败";
            showDocumentMessage(error.message, "error");
        }
    }

    async function uploadDocument(event) {
        event.preventDefault();
        if (state.uploadBusy) {
            return;
        }
        if (!state.selectedKnowledgeBaseId) {
            showDocumentMessage("请先选择知识库", "error");
            return;
        }

        const file = els.documentFileInput.files && els.documentFileInput.files[0];
        if (!file) {
            showDocumentMessage("请选择要上传的文档", "error");
            return;
        }

        const validationError = validateFile(file);
        if (validationError) {
            showDocumentMessage(validationError, "error");
            return;
        }

        const formData = new FormData();
        formData.append("file", file);
        setUploadBusy(true);
        try {
            await window.AicsApi.requestJson(
                    "/api/knowledge-bases/" + state.selectedKnowledgeBaseId + "/documents",
                    {method: "POST", body: formData}
            );
            els.documentFileInput.value = "";
            updateSelectedFileText();
            showDocumentMessage("文档上传并解析完成", "success");
            await loadDocuments();
        } catch (error) {
            showDocumentMessage(error.message, "error");
            await loadDocuments();
        } finally {
            setUploadBusy(false);
        }
    }

    async function deleteDocument(documentId) {
        if (!documentId || state.uploadBusy) {
            return;
        }
        if (!window.confirm("确定删除这个文档吗？")) {
            return;
        }

        setUploadBusy(true);
        try {
            await window.AicsApi.requestJson("/api/knowledge-bases/documents/" + documentId, {
                method: "DELETE"
            });
            showDocumentMessage("文档已删除", "success");
            await loadDocuments();
        } catch (error) {
            showDocumentMessage(error.message, "error");
        } finally {
            setUploadBusy(false);
        }
    }

    function renderDocuments(items) {
        els.documentList.innerHTML = "";
        els.documentListHint.textContent = items.length ? ("共 " + items.length + " 个文档") : "暂无文档";
        if (!items.length) {
            const empty = document.createElement("div");
            empty.className = "empty-state";
            empty.innerHTML = "<strong>暂无文档</strong><span>上传文档后，客服对话可以基于该知识库回答。</span>";
            els.documentList.appendChild(empty);
            return;
        }

        items.forEach(function (item) {
            const row = document.createElement("article");
            row.className = "document-item";

            const main = document.createElement("div");
            main.className = "document-main";

            const title = document.createElement("strong");
            title.textContent = item.fileName || ("文档 " + item.id);

            const meta = document.createElement("span");
            meta.textContent = [
                (item.fileType || "-").toUpperCase(),
                "ID: " + item.id,
                formatDate(item.createdAt)
            ].filter(Boolean).join(" / ");

            main.appendChild(title);
            main.appendChild(meta);
            if (item.errorMessage) {
                const error = document.createElement("span");
                error.className = "document-error";
                error.textContent = item.errorMessage;
                main.appendChild(error);
            }

            const status = document.createElement("span");
            status.className = "status-badge " + statusClass(item.parseStatus);
            status.textContent = statusText(item.parseStatus);

            const deleteBtn = document.createElement("button");
            deleteBtn.className = "danger-btn compact-btn";
            deleteBtn.type = "button";
            deleteBtn.textContent = "删除";
            deleteBtn.addEventListener("click", function () {
                deleteDocument(item.id);
            });

            row.appendChild(main);
            row.appendChild(status);
            row.appendChild(deleteBtn);
            els.documentList.appendChild(row);
        });
    }

    function renderDocumentEmpty(message) {
        els.documentList.innerHTML = "";
        els.documentListHint.textContent = message;
        const empty = document.createElement("div");
        empty.className = "empty-state";
        empty.innerHTML = "<strong>暂无文档</strong><span>选择知识库后可上传和管理文档。</span>";
        els.documentList.appendChild(empty);
        renderSelectedKnowledgeBase();
    }

    function setBusy(busy) {
        state.busy = busy;
        els.createKnowledgeBtn.disabled = busy;
        els.knowledgeNameInput.disabled = busy;
        els.knowledgeDescInput.disabled = busy;
        els.createKnowledgeBtn.textContent = busy ? "创建中" : "创建知识库";
    }

    function setUploadBusy(busy) {
        state.uploadBusy = busy;
        renderSelectedKnowledgeBase();
        els.uploadDocumentBtn.textContent = busy ? "处理中" : "上传文档";
    }

    function showMessage(message, type) {
        els.knowledgeMessage.textContent = message || "";
        els.knowledgeMessage.className = "form-message " + (type || "");
    }

    function showDocumentMessage(message, type) {
        els.documentMessage.textContent = message || "";
        els.documentMessage.className = "form-message " + (type || "");
    }

    function updateSelectedFileText() {
        const file = els.documentFileInput.files && els.documentFileInput.files[0];
        els.documentFileText.textContent = file
                ? file.name + "（" + formatFileSize(file.size) + "）"
                : "选择 TXT / MD / PDF / DOCX 文件，最大 20MB";
    }

    function validateFile(file) {
        if (file.size > MAX_UPLOAD_SIZE) {
            return "上传文件不能超过 20MB";
        }
        const extension = file.name.includes(".")
                ? file.name.slice(file.name.lastIndexOf(".") + 1).toLowerCase()
                : "";
        if (!SUPPORTED_EXTENSIONS.includes(extension)) {
            return "不支持的文件类型，仅支持 TXT、MD、PDF、DOCX";
        }
        return "";
    }

    function statusText(status) {
        if (status === "DONE") {
            return "解析完成";
        }
        if (status === "FAILED") {
            return "解析失败";
        }
        if (status === "PROCESSING") {
            return "解析中";
        }
        return status || "未知";
    }

    function statusClass(status) {
        if (status === "DONE") {
            return "done";
        }
        if (status === "FAILED") {
            return "failed";
        }
        return "processing";
    }

    function formatFileSize(size) {
        if (!Number.isFinite(size)) {
            return "-";
        }
        if (size < 1024 * 1024) {
            return Math.max(1, Math.round(size / 1024)) + "KB";
        }
        return (size / 1024 / 1024).toFixed(1) + "MB";
    }

    function formatDate(value) {
        if (!value) {
            return "";
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return String(value).replace("T", " ");
        }
        return date.toLocaleString("zh-CN", {hour12: false});
    }
})();
