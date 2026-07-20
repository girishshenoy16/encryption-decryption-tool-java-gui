/* Encryption & Decryption Tool — site interactions */
(function () {
  "use strict";

  /* ----- Theme: respect saved pref or system ----- */
  var root = document.documentElement;
  var saved = localStorage.getItem("theme");
  if (saved) {
    root.setAttribute("data-theme", saved);
  } else if (window.matchMedia && window.matchMedia("(prefers-color-scheme: light)").matches) {
    root.setAttribute("data-theme", "light");
  }

  var toggle = document.getElementById("themeToggle");
  if (toggle) {
    toggle.addEventListener("click", function () {
      var next = root.getAttribute("data-theme") === "light" ? "dark" : "light";
      root.setAttribute("data-theme", next);
      localStorage.setItem("theme", next);
    });
  }

  /* ----- Mobile nav ----- */
  var burger = document.getElementById("navBurger");
  var links = document.getElementById("navLinks");
  if (burger && links) {
    burger.addEventListener("click", function () {
      var open = links.classList.toggle("open");
      burger.setAttribute("aria-expanded", open ? "true" : "false");
    });
    links.querySelectorAll("a").forEach(function (a) {
      a.addEventListener("click", function () {
        links.classList.remove("open");
        burger.setAttribute("aria-expanded", "false");
      });
    });
  }

  /* ----- Reveal on scroll ----- */
  var reveals = document.querySelectorAll(".reveal");
  if ("IntersectionObserver" in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) {
          e.target.classList.add("show");
          io.unobserve(e.target);
        }
      });
    }, { threshold: 0.12 });
    reveals.forEach(function (el) { io.observe(el); });
  } else {
    reveals.forEach(function (el) { el.classList.add("show"); });
  }

  /* ----- Lightbox gallery ----- */
  var lightbox = document.getElementById("lightbox");
  var lightboxImg = document.getElementById("lightboxImg");
  var lightboxClose = document.getElementById("lightboxClose");
  document.querySelectorAll(".shot img").forEach(function (img) {
    img.addEventListener("click", function () {
      if (!lightbox) return;
      lightboxImg.src = img.src;
      lightboxImg.alt = img.alt;
      lightbox.classList.add("open");
      lightbox.setAttribute("aria-hidden", "false");
    });
  });
  function closeLightbox() {
    if (!lightbox) return;
    lightbox.classList.remove("open");
    lightbox.setAttribute("aria-hidden", "true");
  }
  if (lightboxClose) lightboxClose.addEventListener("click", closeLightbox);
  if (lightbox) lightbox.addEventListener("click", function (e) {
    if (e.target === lightbox) closeLightbox();
  });
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape") closeLightbox();
  });

  /* ----- Active nav link on scroll ----- */
  var sections = document.querySelectorAll("main section[id]");
  var navAnchors = links ? links.querySelectorAll("a") : [];
  if ("IntersectionObserver" in window && sections.length) {
    var spy = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) {
          var id = e.target.getAttribute("id");
          navAnchors.forEach(function (a) {
            a.classList.toggle("active", a.getAttribute("href") === "#" + id);
          });
        }
      });
    }, { rootMargin: "-45% 0px -50% 0px" });
    sections.forEach(function (s) { spy.observe(s); });
  }

  /* ----- Year ----- */
  var year = document.getElementById("year");
  if (year) year.textContent = new Date().getFullYear();

  /* ----- Try It Online dashboard (client-side Web Crypto) ----- */
  (function dashboard() {
    var encInput = document.getElementById("enc-input");
    var encPass = document.getElementById("enc-pass");
    var decInput = document.getElementById("dec-input");
    var decPass = document.getElementById("dec-pass");
    if (!encInput || !window.crypto || !crypto.subtle) {
      var dash = document.getElementById("dashboard");
      if (dash) {
        dash.insertAdjacentHTML("beforeend",
          '<p style="text-align:center;color:var(--text-muted)">Your browser does not support the Web Crypto API required for this demo.</p>');
      }
      return;
    }

    var MAGIC = "EDT1";
    var ITERATIONS = 600000;
    var SALT_LEN = 16, IV_LEN = 12;

    function b64encode(bytes) {
      var bin = "";
      var arr = new Uint8Array(bytes);
      for (var i = 0; i < arr.length; i++) bin += String.fromCharCode(arr[i]);
      return btoa(bin);
    }
    function b64decode(str) {
      var bin = atob(str.trim());
      var arr = new Uint8Array(bin.length);
      for (var i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
      return arr;
    }
    function strToBytes(str) { return new TextEncoder().encode(str); }
    function bytesToStr(bytes) { return new TextDecoder().decode(bytes); }
    function getRandom(n) { var a = new Uint8Array(n); crypto.getRandomValues(a); return a; }

    async function deriveKey(password, salt) {
      var baseKey = await crypto.subtle.importKey(
        "raw", strToBytes(password), "PBKDF2", false, ["deriveKey"]);
      return crypto.subtle.deriveKey(
        { name: "PBKDF2", salt: salt, iterations: ITERATIONS, hash: "SHA-256" },
        baseKey, { name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]);
    }

    async function encryptText(text, password) {
      var salt = getRandom(SALT_LEN);
      var iv = getRandom(IV_LEN);
      var key = await deriveKey(password, salt);
      var ct = await crypto.subtle.encrypt(
        { name: "AES-GCM", iv: iv }, key, strToBytes(text));
      var ctBytes = new Uint8Array(ct);
      // Build payload: MAGIC(4) | version(1) | saltLen(1) | ivLen(1) | salt | iv | ct
      var out = new Uint8Array(4 + 1 + 1 + 1 + SALT_LEN + IV_LEN + ctBytes.length);
      var dv = new DataView(out.buffer);
      for (var i = 0; i < 4; i++) out[i] = MAGIC.charCodeAt(i);
      out[4] = 1; out[5] = SALT_LEN; out[6] = IV_LEN;
      out.set(salt, 7);
      out.set(iv, 7 + SALT_LEN);
      out.set(ctBytes, 7 + SALT_LEN + IV_LEN);
      return "EDT1" + b64encode(out);
    }

    async function decryptText(b64, password) {
      if (b64.indexOf(MAGIC) === 0) b64 = b64.slice(MAGIC.length);
      var raw = b64decode(b64);
      if (raw.length < 7 + SALT_LEN + IV_LEN) throw new Error("Invalid ciphertext");
      var magic = String.fromCharCode.apply(null, Array.from(raw.slice(0, 4)));
      if (magic !== MAGIC) throw new Error("Not an EDT1 payload");
      var saltLen = raw[5], ivLen = raw[6];
      var salt = raw.slice(7, 7 + saltLen);
      var iv = raw.slice(7 + saltLen, 7 + saltLen + ivLen);
      var ct = raw.slice(7 + saltLen + ivLen);
      var key = await deriveKey(password, salt);
      var pt = await crypto.subtle.decrypt({ name: "AES-GCM", iv: iv }, key, ct);
      return bytesToStr(pt);
    }

    /* Tabs */
    var tabBtns = document.querySelectorAll(".dash .tab-btn");
    var panels = { "d-encrypt": "d-encrypt", "d-decrypt": "d-decrypt", "d-about": "d-about" };
    tabBtns.forEach(function (btn) {
      btn.addEventListener("click", function () {
        var target = btn.getAttribute("data-tab");
        tabBtns.forEach(function (b) { b.classList.toggle("active", b === btn); });
        Object.keys(panels).forEach(function (p) {
          document.getElementById(p).hidden = (p !== target);
        });
      });
    });

    /* Password toggle */
    document.querySelectorAll(".dash .toggle-pass").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var el = document.getElementById(btn.getAttribute("data-target"));
        if (el) el.type = el.type === "password" ? "text" : "password";
      });
    });

    /* Strength meter */
    var bar = document.getElementById("enc-strength-bar");
    var lbl = document.getElementById("enc-strength-label");
    encPass.addEventListener("input", function () {
      var v = this.value;
      var s = 0;
      if (v.length >= 6) s++;
      if (v.length >= 10) s++;
      if (v.length >= 12) s++;
      if (/[A-Z]/.test(v)) s++;
      if (/[0-9]/.test(v)) s++;
      if (/[^A-Za-z0-9]/.test(v)) s++;
      var levels = [
        { w: "0%", c: "", l: "" },
        { w: "25%", c: "var(--danger)", l: "Weak" },
        { w: "50%", c: "var(--primary)", l: "Fair" },
        { w: "75%", c: "var(--primary)", l: "Good" },
        { w: "100%", c: "var(--accent)", l: "Strong" }
      ];
      var lv = levels[Math.min(s, 4)];
      bar.style.width = lv.w; bar.style.background = lv.c;
      lbl.style.color = lv.c; lbl.textContent = lv.l;
    });

    function showResult(prefix, text) {
      document.getElementById(prefix + "-result-text").textContent = text;
      document.getElementById(prefix + "-result").classList.add("show");
      document.getElementById(prefix + "-error").classList.remove("show");
    }
    function showError(prefix, msg) {
      document.getElementById(prefix + "-error-text").textContent = msg;
      document.getElementById(prefix + "-error").classList.add("show");
      document.getElementById(prefix + "-result").classList.remove("show");
    }

    document.getElementById("enc-btn").addEventListener("click", async function () {
      var btn = this;
      var text = encInput.value.trim();
      var pass = encPass.value;
      if (!text) return showError("enc", "Please enter text to encrypt.");
      if (pass.length < 12) return showError("enc", "Password must be at least 12 characters.");
      btn.disabled = true; btn.innerHTML = '<span class="spinner"></span> Encrypting...';
      try {
        var out = await encryptText(text, pass);
        showResult("enc", out);
      } catch (e) {
        showError("enc", "Encryption failed: " + e.message);
      } finally {
        btn.disabled = false; btn.innerHTML = "🔒 Encrypt";
      }
    });

    document.getElementById("dec-btn").addEventListener("click", async function () {
      var btn = this;
      var b64 = decInput.value.trim();
      var pass = decPass.value;
      if (!b64) return showError("dec", "Please paste the ciphertext.");
      if (!pass) return showError("dec", "Please enter the password.");
      btn.disabled = true; btn.innerHTML = '<span class="spinner"></span> Decrypting...';
      try {
        var out = await decryptText(b64, pass);
        showResult("dec", out);
      } catch (e) {
        showError("dec", "Decryption failed — wrong password or corrupted data.");
      } finally {
        btn.disabled = false; btn.innerHTML = "🔓 Decrypt";
      }
    });

    /* Copy */
    document.querySelectorAll(".dash .btn-copy").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var txt = document.getElementById(btn.getAttribute("data-copy")).textContent;
        if (txt) navigator.clipboard.writeText(txt).then(function () {
          var old = btn.textContent; btn.textContent = "✓ Copied";
          setTimeout(function () { btn.textContent = old; }, 1400);
        });
      });
    });

    /* Clear */
    document.querySelectorAll(".dash .btn-clear").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var which = btn.getAttribute("data-clear");
        if (which === "enc") {
          encInput.value = ""; encPass.value = "";
          bar.style.width = "0%"; lbl.textContent = "";
        } else {
          decInput.value = ""; decPass.value = "";
        }
        document.getElementById(which + "-result").classList.remove("show");
        document.getElementById(which + "-error").classList.remove("show");
      });
    });
  })();
})();
