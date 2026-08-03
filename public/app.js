import { initializeApp } from "https://www.gstatic.com/firebasejs/12.16.0/firebase-app.js";
import {
    GoogleAuthProvider,
    getAuth,
    onAuthStateChanged,
    signInWithPopup,
    signOut
} from "https://www.gstatic.com/firebasejs/12.16.0/firebase-auth.js";
import {
    getDatabase,
    onValue,
    ref,
    serverTimestamp,
    set
} from "https://www.gstatic.com/firebasejs/12.16.0/firebase-database.js";

const ADMIN_EMAIL = "t1110h1405@gmail.com";
const firebaseConfig = {
    apiKey: "AIzaSyD6J-fg2Y31U2eYo4y7fcZdfET1GiHdMIQ",
    authDomain: "autoswipe-t1110h1405.firebaseapp.com",
    databaseURL: "https://autoswipe-t1110h1405-default-rtdb.asia-southeast1.firebasedatabase.app",
    projectId: "autoswipe-t1110h1405",
    storageBucket: "autoswipe-t1110h1405.firebasestorage.app",
    messagingSenderId: "515744945939",
    appId: "1:515744945939:web:bccfe0f99af7f35436ece6"
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const database = getDatabase(app);
const sharedUrlRef = ref(database, "sharedUrl");

const loginButton = document.querySelector("#login-button");
const logoutButton = document.querySelector("#logout-button");
const saveButton = document.querySelector("#save-button");
const clearButton = document.querySelector("#clear-button");
const urlInput = document.querySelector("#url-input");
const signedOutPanel = document.querySelector("#signed-out-panel");
const adminPanel = document.querySelector("#admin-panel");
const accountEmail = document.querySelector("#account-email");
const currentLink = document.querySelector("#current-link");
const emptyMessage = document.querySelector("#empty-message");
const updatedAt = document.querySelector("#updated-at");
const status = document.querySelector("#status");

function setStatus(message, isError = false) {
    status.textContent = message;
    status.classList.toggle("error", isError);
}

function isAuthorized(user) {
    return Boolean(user && user.email === ADMIN_EMAIL && user.emailVerified);
}

function normalizeUrl(value) {
    const trimmed = value.trim();
    if (!trimmed) {
        throw new Error("URLを入力してください。");
    }
    if (trimmed.length > 2048) {
        throw new Error("URLが長すぎます。");
    }
    let parsed;
    try {
        parsed = new URL(trimmed);
    } catch {
        throw new Error("正しいURLを入力してください。");
    }
    if (parsed.protocol !== "https:" && parsed.protocol !== "http:") {
        throw new Error("http:// または https:// のURLだけ使用できます。");
    }
    return parsed.href;
}

function setBusy(busy) {
    saveButton.disabled = busy;
    clearButton.disabled = busy;
}

onValue(sharedUrlRef, (snapshot) => {
    const value = snapshot.val() || {};
    const url = typeof value.url === "string" ? value.url : "";
    if (url) {
        currentLink.href = url;
        currentLink.textContent = url;
        currentLink.hidden = false;
        emptyMessage.hidden = true;
        if (document.activeElement !== urlInput) {
            urlInput.value = url;
        }
    } else {
        currentLink.removeAttribute("href");
        currentLink.textContent = "";
        currentLink.hidden = true;
        emptyMessage.hidden = false;
        if (document.activeElement !== urlInput) {
            urlInput.value = "";
        }
    }
    updatedAt.textContent = Number.isFinite(value.updatedAt)
        ? `更新: ${new Date(value.updatedAt).toLocaleString("ja-JP")}`
        : "";
}, () => setStatus("現在のリンクを取得できませんでした。", true));

onAuthStateChanged(auth, async (user) => {
    if (user && !isAuthorized(user)) {
        await signOut(auth);
        setStatus("このアカウントには変更権限がありません。", true);
        return;
    }
    const authorized = isAuthorized(user);
    signedOutPanel.hidden = authorized;
    adminPanel.hidden = !authorized;
    accountEmail.textContent = authorized ? user.email : "";
});

loginButton.addEventListener("click", async () => {
    setStatus("ログインしています…");
    try {
        const provider = new GoogleAuthProvider();
        provider.setCustomParameters({ prompt: "select_account" });
        await signInWithPopup(auth, provider);
        setStatus("ログインしました。");
    } catch (error) {
        setStatus(error.code === "auth/popup-closed-by-user"
            ? "ログインをキャンセルしました。"
            : `ログインできませんでした（${error.code || "unknown"}）。`, true);
    }
});

logoutButton.addEventListener("click", async () => {
    await signOut(auth);
    setStatus("ログアウトしました。");
});

saveButton.addEventListener("click", async () => {
    if (!isAuthorized(auth.currentUser)) {
        setStatus("管理者としてログインしてください。", true);
        return;
    }
    let url;
    try {
        url = normalizeUrl(urlInput.value);
    } catch (error) {
        setStatus(error.message, true);
        return;
    }
    setBusy(true);
    setStatus("全端末に反映しています…");
    try {
        await set(sharedUrlRef, { url, updatedAt: serverTimestamp() });
        setStatus("全端末に反映しました。");
    } catch {
        setStatus("保存できませんでした。権限または通信状態を確認してください。", true);
    } finally {
        setBusy(false);
    }
});

clearButton.addEventListener("click", async () => {
    if (!isAuthorized(auth.currentUser)) {
        setStatus("管理者としてログインしてください。", true);
        return;
    }
    setBusy(true);
    setStatus("リンクを削除しています…");
    try {
        await set(sharedUrlRef, { url: "", updatedAt: serverTimestamp() });
        setStatus("リンクを全端末から削除しました。");
    } catch {
        setStatus("削除できませんでした。権限または通信状態を確認してください。", true);
    } finally {
        setBusy(false);
    }
});
