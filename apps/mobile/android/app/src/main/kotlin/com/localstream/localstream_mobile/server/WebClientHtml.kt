package com.localstream.localstream_mobile.server

/**
 * Renders the browser client served by the Android server.
 *
 * The page is a dependency-free single-page application that consumes the
 * existing API only:
 *   GET /api/v1/files        - library listing
 *   GET /api/v1/files/:id    - single item detail
 *   GET /api/v1/stream/:id   - HTTP Range streaming (never downloaded clientside)
 *   GET /api/v1/status       - connection monitor heartbeat (polling, no WS)
 *
 * This is a client-only upgrade. No backend behavior, endpoints, or streaming
 * behavior is changed. The optional [watchId] deep-links this shell to the
 * same player overlay so the existing /watch/:id route keeps working.
 *
 * NOTE: keep the embedded JavaScript free of Kotlin string-interpolation
 * sequences. No template literals, no '$' identifiers.
 */
object WebClientHtml {

    fun render(watchId: String? = null): String {
        val body = watchId?.let { """<body data-watch-id="${it}">""" } ?: "<body>"
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
            <meta name="theme-color" content="#0d1117">
            <title>LocalStream Media</title>
            <style>
            :root{
                color-scheme: dark;
                --bg:#0d1117; --bg-grad:#0a0e14; --surface:#161b22; --surface-2:#1c2129;
                --border:#30363d; --border-soft:rgba(48,54,61,.55);
                --text:#f0f6fc; --muted:#8b949e;
                --accent:#58a6ff; --good:#3fb950; --warn:#d29922; --bad:#f85149;
                --video-grad:linear-gradient(140deg,#1f6feb 0%,#2f81f7 45%,#58a6ff 100%);
                --audio-grad:linear-gradient(140deg,#8957e5 0%,#a371f7 45%,#bc8cff 100%);
                --image-grad:linear-gradient(140deg,#238636 0%,#2ea043 45%,#56d364 100%);
                --other-grad:linear-gradient(140deg,#30363d 0%,#484f58 45%,#6e7681 100%);
                --radius:14px;
                --ease:cubic-bezier(.4,0,.2,1);
            }
            *{margin:0;padding:0;box-sizing:border-box}
            html,body{height:100%}
            body{
                font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif;
                background:var(--bg);
                background-image:radial-gradient(1100px 500px at 50% -10%,rgba(31,111,235,.10),transparent 60%);
                background-attachment:fixed;
                color:var(--text);
                -webkit-font-smoothing:antialiased;
                min-height:100vh;
                line-height:1.45;
            }
            button{font:inherit;color:inherit;background:none;border:none}
            a{color:var(--accent);text-decoration:none}
            .icon{
                width:18px;height:18px;flex:none;
                fill:none;stroke:currentColor;stroke-width:2;
                stroke-linecap:round;stroke-linejoin:round;
            }
            .icon--big{width:34px;height:34px;stroke-width:1.6}

            /* Layout */
            .shell{max-width:1200px;margin:0 auto;padding:18px 20px 64px}

            .topbar{
                display:flex;align-items:center;gap:18px;flex-wrap:wrap;
                padding:10px 2px 16px;
            }
            .brand{display:flex;align-items:center;gap:12px;min-width:0}
            .brand-mark{
                width:40px;height:40px;border-radius:12px;flex:none;
                background:linear-gradient(160deg,rgba(255,255,255,.16),rgba(255,255,255,.05));
                border:1px solid var(--border-soft);
                display:flex;align-items:center;justify-content:center;
                color:var(--accent);
            }
            .brand h1{font-size:19px;letter-spacing:-.02em;font-weight:700}
            .brand .sub{font-size:12px;color:var(--muted)}
            .topbar-search{flex:1 1 260px;min-width:180px;max-width:520px;margin-left:auto}
            .search{
                width:100%;padding:11px 14px;border-radius:12px;
                background:var(--surface);border:1px solid var(--border);
                color:var(--text);font-size:14px;outline:none;
                transition:border-color .15s var(--ease), box-shadow .15s var(--ease);
            }
            .search:focus{border-color:var(--accent);box-shadow:0 0 0 3px rgba(88,166,255,.18)}
            .search::-webkit-search-cancel-button{-webkit-appearance:none}
            .conn{display:flex;align-items:center;gap:9px;flex:none;font-size:13px;color:var(--muted)}
            .conn-dot{width:10px;height:10px;border-radius:50%;background:var(--muted);transition:background .2s var(--ease)}
            .conn-dot.connected{background:var(--good);box-shadow:0 0 0 4px rgba(63,185,80,.14)}
            .conn-dot.reconnecting{background:var(--warn);animation:pulse 1.1s ease-in-out infinite}
            .conn-dot.offline{background:var(--bad);box-shadow:0 0 0 4px rgba(248,81,73,.14)}
            @keyframes pulse{0%,100%{opacity:1}50%{opacity:.35}}

            .toolbar{display:flex;align-items:center;gap:10px;flex-wrap:wrap;padding:2px 0 20px}
            .tabs{display:flex;gap:6px;flex-wrap:wrap}
            .tab{
                padding:8px 16px;border-radius:999px;font-size:13px;font-weight:600;
                color:var(--muted);border:1px solid var(--border-soft);
                cursor:pointer;transition:color .15s var(--ease),background .15s var(--ease),border-color .15s var(--ease);
            }
            .tab:hover{color:var(--text);border-color:var(--border)}
            .tab.active{color:#0d1117;background:var(--accent);border-color:var(--accent)}
            .spacer{flex:1}
            .count{font-size:12px;color:var(--muted);white-space:nowrap}
            select.sort{
                padding:8px 12px;border-radius:999px;font-size:13px;
                background:var(--surface);border:1px solid var(--border);color:var(--text);cursor:pointer;outline:none;
            }

            /* Media grid + cards */
            .grid{
                display:grid;gap:18px;
                grid-template-columns:repeat(auto-fill,minmax(210px,1fr));
            }
            .card{
                background:var(--surface);border:1px solid var(--border);
                border-radius:var(--radius);overflow:hidden;
                display:flex;flex-direction:column;
                cursor:pointer;outline:none;
                transition:transform .16s var(--ease),border-color .16s var(--ease),box-shadow .16s var(--ease);
            }
            .card:hover,.card:focus-visible{
                transform:translateY(-3px);border-color:var(--accent);
                box-shadow:0 14px 32px rgba(0,0,0,.4);
            }
            .card:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
            .cover{
                aspect-ratio:16/10;position:relative;display:flex;
                align-items:center;justify-content:center;color:rgba(255,255,255,.85);
            }
            .cover--video{background:var(--video-grad)}
            .cover--audio{background:var(--audio-grad)}
            .cover--image{background:var(--image-grad)}
            .cover--other{background:var(--other-grad)}
            .cover-tag{
                position:absolute;top:10px;right:10px;font-size:10px;font-weight:700;
                letter-spacing:.06em;padding:3px 8px;border-radius:999px;
                background:rgba(0,0,0,.32);color:rgba(255,255,255,.92);
                text-transform:uppercase;
            }
            .card.unavailable .cover{filter:grayscale(.85) brightness(.6)}
            .unav-tag{
                position:absolute;left:10px;bottom:10px;font-size:10px;font-weight:700;
                padding:3px 8px;border-radius:999px;background:rgba(248,81,73,.16);
                border:1px solid rgba(248,81,73,.5);color:#ffb3ad;
            }
            .card-body{padding:12px 14px 6px;display:flex;flex-direction:column;gap:4px;flex:1;min-width:0}
            .card-title{
                font-size:14px;font-weight:600;overflow:hidden;
                display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;
                overflow-wrap:anywhere;
            }
            .card-meta{font-size:12px;color:var(--muted)}
            .card-foot{display:flex;gap:8px;padding:8px 14px 14px}

            /* Liquid Glass buttons — buttons ONLY, never surfaces */
            .glass-btn{
                position:relative;display:inline-flex;align-items:center;justify-content:center;gap:7px;
                min-height:42px;padding:9px 16px;border-radius:12px;
                border:1px solid rgba(255,255,255,.16);
                background:linear-gradient(180deg,rgba(255,255,255,.15),rgba(255,255,255,.05));
                -webkit-backdrop-filter:blur(12px);backdrop-filter:blur(12px);
                color:#fff;font-weight:600;font-size:13px;
                box-shadow:0 4px 16px rgba(0,0,0,.28), inset 0 1px 0 rgba(255,255,255,.22);
                cursor:pointer;text-decoration:none;white-space:nowrap;
                transition:transform .12s var(--ease),filter .12s var(--ease),box-shadow .12s var(--ease);
            }
            .glass-btn:hover{filter:brightness(1.12)}
            .glass-btn:active{transform:scale(.97)}
            .glass-btn:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
            .glass-btn--accent{
                background:linear-gradient(180deg,rgba(88,166,255,.45),rgba(88,166,255,.22));
                border-color:rgba(88,166,255,.4);
            }
            .glass-btn--icon{width:44px;height:44px;padding:0;border-radius:12px;flex:none}
            .glass-btn--sm{min-height:36px;padding:7px 13px;border-radius:10px;font-size:12px}
            .glass-btn--sm.glass-btn--icon{width:36px;height:36px}
            .glass-btn--lg{min-height:52px;padding:12px 26px;font-size:15px;border-radius:16px}
            .glass-btn[disabled]{opacity:.45;pointer-events:none}

            .card.unavailable .card-foot{opacity:.5}

            /* Folder browsing */
            .folderbar{display:flex;align-items:center;gap:8px;flex-wrap:wrap;padding:2px 0 8px}
            .folder-path{display:flex;align-items:center;gap:4px;font-size:13px;color:var(--muted);min-width:0}
            .folder-path .crumb-sep{opacity:.5}
            .folder-path .crumb{color:var(--accent);cursor:pointer;background:none;border:none;font:inherit;padding:2px 6px;border-radius:6px}
            .folder-path .crumb:hover{background:rgba(88,166,255,.14)}
            .folder-path .crumb-current{color:var(--text);font-weight:600;padding:2px 6px}
            .folders{display:flex;gap:8px;flex-wrap:wrap;padding:0 0 18px}
            .folders[hidden]{display:none}
            .folder-card{
                display:flex;align-items:center;gap:10px;max-width:240px;text-align:left;
                padding:10px 14px;border-radius:12px;border:1px solid var(--border);
                background:var(--surface);cursor:pointer;color:var(--text);
                transition:border-color .15s var(--ease),transform .15s var(--ease),box-shadow .15s var(--ease);
            }
            .folder-card:hover,.folder-card:focus-visible{
                border-color:var(--accent);transform:translateY(-2px);
                box-shadow:0 10px 24px rgba(0,0,0,.35);
            }
            .folder-card:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
            .folder-icon{width:34px;height:34px;border-radius:10px;flex:none;display:flex;align-items:center;justify-content:center;color:#d29922;background:rgba(210,153,34,.14)}
            .folder-name{font-size:13px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
            .folder-count{font-size:11px;color:var(--muted)}

            /* Card thumbnails */
            .cover img.thumb{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;display:block;background:#000}
            .cover img.thumb[hidden]{display:none}
            .cover .dur-badge{
                position:absolute;left:10px;bottom:10px;font-size:10px;font-weight:700;
                padding:2px 8px;border-radius:999px;background:rgba(0,0,0,.55);color:#fff;
                font-variant-numeric:tabular-nums;
            }
            .card.unavailable .cover .dur-badge{display:none}

            /* Access PIN login */
            .login{
                position:fixed;inset:0;z-index:70;display:flex;align-items:center;justify-content:center;
                background:rgba(5,8,12,.72);-webkit-backdrop-filter:blur(8px);backdrop-filter:blur(8px);
                padding:20px;
            }
            .login[hidden]{display:none}
            .login-panel{
                width:min(100%,380px);background:rgba(22,27,34,.96);border:1px solid var(--border);
                border-radius:18px;padding:28px;display:flex;flex-direction:column;gap:14px;
                box-shadow:0 30px 80px rgba(0,0,0,.5);text-align:center;
            }
            .login-panel h2{font-size:18px}
            .login-panel p{font-size:13px;color:var(--muted);line-height:1.5}
            .login-panel input{
                width:100%;padding:13px 14px;border-radius:12px;background:var(--surface);
                border:1px solid var(--border);color:var(--text);font-size:18px;
                letter-spacing:.35em;text-align:center;outline:none;
            }
            .login-panel input:focus{border-color:var(--accent);box-shadow:0 0 0 3px rgba(88,166,255,.18)}
            .login-err{font-size:12px;color:var(--bad);min-height:16px}
            .login-hint{font-size:11px;color:var(--muted)}

            /* Player subtitle menu */
            .controls-wrap{position:relative}
            .controls{
                position:relative;z-index:6;
            }
            .sub-menu{
                position:absolute;bottom:calc(100% + 10px);left:14px;display:none;flex-direction:column;gap:2px;
                background:rgba(13,17,23,.96);border:1px solid var(--border);border-radius:12px;padding:6px;min-width:170px;z-index:60;
                -webkit-backdrop-filter:blur(12px);backdrop-filter:blur(12px);
            }
            .sub-menu.open{display:flex}
            .sub-menu button{
                text-align:left;padding:8px 12px;border-radius:8px;font-size:12px;color:var(--muted);cursor:pointer;
            }
            .sub-menu button:hover{background:rgba(255,255,255,.08);color:var(--text)}
            .sub-menu button.active{color:#0d1117;background:var(--accent)}

            /* Skeletons */
            .skel{
                pointer-events:none;height:100%;
                background:var(--surface);border:1px solid var(--border);border-radius:var(--radius);
                display:flex;flex-direction:column;overflow:hidden;
            }
            .skel-cover{aspect-ratio:16/10;background:linear-gradient(90deg,#161b22 25%,#1f262f 37%,#161b22 63%);background-size:400% 100%;animation:shimmer 1.3s ease infinite}
            .skel-lines{flex:1;padding:12px 14px;display:flex;flex-direction:column;gap:8px}
            @keyframes shimmer{0%{background-position:100% 0}100%{background-position:0 0}}

            /* Empty + toast */
            .empty{padding:64px 20px;text-align:center;color:var(--muted)}
            .empty h2{font-size:18px;color:var(--text);margin-bottom:8px}
            .empty p{font-size:13px;max-width:420px;margin:0 auto 20px}
            .toast{
                position:fixed;left:50%;bottom:24px;transform:translateX(-50%) translateY(16px);
                background:rgba(22,27,34,.96);border:1px solid var(--border);
                color:var(--text);font-size:13px;padding:12px 18px;border-radius:12px;
                box-shadow:0 10px 30px rgba(0,0,0,.4);opacity:0;pointer-events:none;
                transition:opacity .2s var(--ease),transform .2s var(--ease);z-index:60;max-width:min(92vw,560px);
            }
            .toast.show{opacity:1;transform:translateX(-50%) translateY(0)}

            /* Player */
            .player{
                position:fixed;inset:0;z-index:50;
                background:rgba(5,8,12,.94);-webkit-backdrop-filter:blur(6px);backdrop-filter:blur(6px);
                display:flex;align-items:center;justify-content:center;padding:20px;
            }
            .player[hidden]{display:none}
            .player-shell{width:min(100%,1060px);display:flex;flex-direction:column;gap:14px}
            .player-top{display:flex;align-items:center;gap:12px}
            .ptitle{flex:1;font-size:16px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;min-width:0}
            .prow{display:flex;gap:8px}
            .stage{
                position:relative;border-radius:14px;overflow:hidden;background:#000;
                border:1px solid var(--border);
                box-shadow:0 30px 80px rgba(0,0,0,.55);
                display:flex;align-items:center;justify-content:center;
            }
            .stage video,.stage audio{width:100%;display:block}
            .stage video{height:auto;object-fit:contain;max-height:calc(100vh - 190px);max-height:calc(100dvh - 190px)}
            .stage audio{height:auto}
            .stage:fullscreen{display:flex;align-items:center;justify-content:center;border-radius:0;border:none}
            .stage:fullscreen video,.stage:fullscreen audio{width:100%;height:100%;object-fit:contain}
            .cover-layer{
                position:absolute;inset:0;display:flex;flex-direction:column;gap:16px;
                align-items:center;justify-content:center;text-align:center;
                background:radial-gradient(600px 300px at 50% 30%,rgba(88,166,255,.12),transparent 70%);
                padding:24px;color:var(--text);
            }
            .cover-layer .hint{font-size:13px;color:var(--muted);max-width:520px}
            .spinner{
                position:absolute;inset:0;display:flex;align-items:center;justify-content:center;pointer-events:none;
            }
            .ring{
                width:46px;height:46px;border-radius:50%;
                border:3px solid rgba(255,255,255,.18);border-top-color:var(--accent);
                animation:spin .9s linear infinite;
            }
            @keyframes spin{to{transform:rotate(360deg)}}
            .perror{
                position:absolute;top:0;left:0;right:0;
                display:flex;flex-direction:column;gap:10px;
                align-items:center;text-align:center;
                padding:14px 16px;background:rgba(5,8,12,.88);
                border-bottom:1px solid var(--border);
                -webkit-backdrop-filter:blur(4px);backdrop-filter:blur(4px);
            }
            .perror .prow{flex-wrap:wrap;justify-content:center}
            .perror p{max-width:640px;font-size:13px;color:var(--text);line-height:1.5;margin:0}
            .perror p .err-code{display:block;margin-top:4px;font-size:11px;color:var(--muted)}

            .controls{
                background:rgba(13,17,23,.82);-webkit-backdrop-filter:blur(14px);backdrop-filter:blur(14px);
                border:1px solid var(--border);border-radius:14px;
                display:flex;align-items:center;gap:12px;padding:10px 14px;
                transition:opacity .25s var(--ease);
            }
            .controls-hidden{opacity:0;pointer-events:none}
            .seek{flex:1;display:flex;flex-direction:column;gap:4px;min-width:0}
            .seekbar{position:relative;height:20px;display:flex;align-items:center;cursor:pointer}
            .seekbar .track,
            .seekbar .buffer{
                position:absolute;left:0;top:9px;height:4px;border-radius:4px;width:100%;pointer-events:none;
            }
            .seekbar .track{background:rgba(255,255,255,.16)}
            .seekbar .buffer{background:rgba(255,255,255,.28);width:0%}
            .seekbar input[type=range]{
                position:relative;width:100%;-webkit-appearance:none;appearance:none;background:transparent;margin:0;height:20px;cursor:pointer;
            }
            .seekbar input[type=range]::-webkit-slider-thumb{
                -webkit-appearance:none;width:14px;height:14px;border-radius:50%;
                background:#fff;border:none;box-shadow:0 1px 6px rgba(0,0,0,.5);
            }
            .seekbar input[type=range]::-moz-range-thumb{width:14px;height:14px;border-radius:50%;background:#fff;border:none}
            .time{font-size:12px;color:var(--muted);font-variant-numeric:tabular-nums}
            input[type=range].vol{width:90px;-webkit-appearance:none;appearance:none;background:rgba(255,255,255,.2);height:4px;border-radius:4px;accent-color:var(--accent)}
            input[type=range].vol::-webkit-slider-thumb{-webkit-appearance:none;width:14px;height:14px;border-radius:50%;background:#fff;border:none}
            input[type=range].vol::-moz-range-thumb{width:14px;height:14px;border-radius:50%;background:#fff;border:none}
            .vol-wrap{display:flex;align-items:center;gap:6px}
            @media (max-width:720px){ input[type=range].vol{width:64px} }

            body.no-scroll{overflow:hidden}
            .footer{margin-top:44px;text-align:center;font-size:12px;color:var(--muted)}

            /* Responsive */
            @media (max-width:640px){
                .shell{padding:12px 14px 48px}
                .topbar{gap:12px}
                .topbar-search{order:3;flex-basis:100%;max-width:none}
                .grid{grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:14px}
                .glass-btn--lg{min-height:48px}
                .player{padding:12px}
                .ptitle{font-size:14px}
            }
            @media (min-width:1440px){.shell{max-width:1360px}}
            @media (prefers-reduced-motion:reduce){*,*::before,*::after{animation-duration:.01ms!important;transition-duration:.01ms!important}}
            </style>
            </head>
            $body
            <svg xmlns="http://www.w3.org/2000/svg" style="display:none" aria-hidden="true">
                <symbol id="i-play" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></symbol>
                <symbol id="i-pause" viewBox="0 0 24 24"><path d="M7 5h3.5v14H7zM13.5 5H17v14h-3.5z"/></symbol>
                <symbol id="i-download" viewBox="0 0 24 24"><path d="M12 3v11m0 0l-4-4m4 4l4-4M5 20h14"/></symbol>
                <symbol id="i-x" viewBox="0 0 24 24"><path d="M18 6L6 18M6 6l12 12"/></symbol>
                <symbol id="i-back" viewBox="0 0 24 24"><path d="M15 18l-6-6 6-6"/></symbol>
                <symbol id="i-full" viewBox="0 0 24 24"><path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"/></symbol>
                <symbol id="i-exit" viewBox="0 0 24 24"><path d="M8 3v3a2 2 0 0 1-2 2H3m18 0h-3a2 2 0 0 1-2-2V3m0 18v-3a2 2 0 0 1 2-2h3M3 16h3a2 2 0 0 1 2 2v3"/></symbol>
                <symbol id="i-vol" viewBox="0 0 24 24"><path d="M11 5L6 9H2v6h4l5 4z"/></symbol>
                <symbol id="i-voloff" viewBox="0 0 24 24"><path d="M11 5L6 9H2v6h4l5 4zM22 9l-6 6M16 9l6 6"/></symbol>
                <symbol id="i-film" viewBox="0 0 24 24"><rect x="2" y="4" width="20" height="16" rx="2"/><path d="M7 4v16M17 4v16M2 9h5M2 15h5M17 9h5M17 15h5"/></symbol>
                <symbol id="i-music" viewBox="0 0 24 24"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></symbol>
                <symbol id="i-image" viewBox="0 0 24 24"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="M21 15l-5-5L5 21"/></symbol>
                <symbol id="i-file" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/></symbol>
                <symbol id="i-retry" viewBox="0 0 24 24"><path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/></symbol>
                <symbol id="i-refresh" viewBox="0 0 24 24"><path d="M21 12a9 9 0 1 1-2.64-6.36M21 3v6h-6"/></symbol>
                <symbol id="i-external" viewBox="0 0 24 24"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><path d="M15 3h6v6M10 14L21 3"/></symbol>
                <symbol id="i-vlc" viewBox="0 0 24 24"><rect x="2" y="3" width="20" height="15" rx="2"/><path d="M10 7.5l5.5 3-5.5 3z"/><path d="M8 21h8M12 18v3"/></symbol>
                <symbol id="i-folder" viewBox="0 0 24 24"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></symbol>
                <symbol id="i-lock" viewBox="0 0 24 24"><rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/></symbol>
                <symbol id="i-sub" viewBox="0 0 24 24"><rect x="2" y="5" width="20" height="14" rx="2"/><path d="M7 15h6M7 12h10M13 15h4"/></symbol>
            </svg>

            <main class="shell">
                <header class="topbar">
                    <div class="brand">
                        <div class="brand-mark"><svg class="icon icon--big"><use href="#i-film"></use></svg></div>
                        <div>
                            <h1>LocalStream</h1>
                            <div class="sub">Local Media Server</div>
                        </div>
                    </div>
                    <div class="topbar-search">
                        <input class="search" id="search" type="search" placeholder="Search media" aria-label="Search media" autocomplete="off">
                    </div>
                    <div class="conn" id="conn"><span class="conn-dot checking" id="connDot"></span><span id="connText" role="status" aria-live="polite">Checking…</span></div>
                </header>

                <nav class="toolbar" aria-label="Media filters">
                    <div class="tabs" id="tabs">
                        <button class="tab active" data-tab="all">All</button>
                        <button class="tab" data-tab="video">Videos</button>
                        <button class="tab" data-tab="audio">Audio</button>
                        <button class="tab" data-tab="image">Images</button>
                    </div>
                    <span class="spacer"></span>
                    <span class="count" id="count"></span>
                    <button class="glass-btn glass-btn--sm" id="refresh" aria-label="Refresh media library"><svg class="icon"><use href="#i-refresh"></use></svg></button>
                    <select class="sort" id="sort" aria-label="Sort media">
                        <option value="name">Name A–Z</option>
                        <option value="sizeDesc">Size largest</option>
                        <option value="sizeAsc">Size smallest</option>
                        <option value="type">Media type</option>
                    </select>
                </nav>

                <nav class="folderbar" id="folderBar" hidden aria-label="Current folder">
                    <button class="glass-btn glass-btn--sm" id="folderUp" aria-label="Go up one folder"><svg class="icon"><use href="#i-back"></use></svg> Up</button>
                    <div class="folder-path" id="folderPath"></div>
                </nav>
                <div class="folders" id="folders" hidden aria-label="Folders"></div>

                <div id="library" class="grid"></div>
                <div id="empty" class="empty" hidden></div>

                <footer class="footer">LocalStream · served from your Android device</footer>
            </main>

            <div class="player" id="player" hidden aria-modal="true" role="dialog" aria-label="Media player">
                <div class="player-shell">
                    <div class="player-top">
                        <div class="ptitle" id="ptitle"></div>
                        <div class="prow">
                            <button class="glass-btn glass-btn--icon" id="pVlc" title="Copy network stream URL for VLC" aria-label="Copy network stream URL for VLC"><svg class="icon"><use href="#i-vlc"></use></svg></button>
                            <button class="glass-btn glass-btn--icon" id="pLink" aria-label="Open stream in a new window"><svg class="icon"><use href="#i-external"></use></svg></button>
                            <button class="glass-btn glass-btn--icon" id="pClose" aria-label="Close player"><svg class="icon"><use href="#i-x"></use></svg></button>
                        </div>
                    </div>
                    <div class="stage" id="stage">
                        <div class="cover-layer" id="cover">
                            <button class="glass-btn glass-btn--accent glass-btn--lg" id="coverPlay"><svg class="icon icon--big"><use href="#i-play"></use></svg> Play</button>
                            <span class="hint" id="coverHint">Tap play to start streaming — only the range you need is transferred.</span>
                        </div>
                        <div class="spinner" id="spinner" hidden><div class="ring"></div></div>
                        <div class="perror" id="perror" hidden>
                            <p><span id="perrorText"></span><span class="err-code" id="perrorCode"></span></p>
                            <div class="prow">
                                <button class="glass-btn glass-btn--sm glass-btn--accent" id="pRetry"><svg class="icon"><use href="#i-retry"></use></svg> Retry</button>
                                <button class="glass-btn glass-btn--sm" id="pVlcErr"><svg class="icon"><use href="#i-vlc"></use></svg> Copy VLC link</button>
                                <a class="glass-btn glass-btn--sm" id="pDownload" href="#" download><svg class="icon"><use href="#i-download"></use></svg> Download</a>
                            </div>
                        </div>
                    </div>
                    <div class="controls-wrap" id="controlswrap">
                        <div class="controls" id="controls">
                            <button class="glass-btn glass-btn--icon" id="cPlay" aria-label="Play">
                                <svg class="icon" id="cPlayIcon"><use href="#i-play"></use></svg>
                            </button>
                            <div class="seek">
                                <div class="seekbar" id="seekbar">
                                    <div class="track"></div>
                                    <div class="buffer" id="cBuffer"></div>
                                    <input type="range" id="cSeek" min="0" max="1000" step="1" value="0" aria-label="Seek">
                                </div>
                                <div class="time" id="cTime">0:00 / 0:00</div>
                            </div>
                            <div class="vol-wrap">
                                <button class="glass-btn glass-btn--icon" id="cMute" aria-label="Mute">
                                    <svg class="icon" id="cMuteIcon"><use href="#i-vol"></use></svg>
                                </button>
                                <input type="range" class="vol" id="cVol" min="0" max="1" step="0.05" value="1" aria-label="Volume">
                            </div>
                            <button class="glass-btn glass-btn--icon" id="cSub" aria-label="Subtitles" hidden>
                                <svg class="icon" id="cSubIcon"><use href="#i-sub"></use></svg>
                            </button>
                            <button class="glass-btn glass-btn--icon" id="cFull" aria-label="Fullscreen">
                                <svg class="icon" id="cFullIcon"><use href="#i-full"></use></svg>
                            </button>
                        </div>
                        <div class="sub-menu" id="subMenu" role="menu" aria-label="Subtitles"></div>
                    </div>
                </div>
            </div>

            <div class="login" id="login" hidden>
                <div class="login-panel">
                    <div class="brand-mark" style="margin:0 auto"><svg class="icon icon--big"><use href="#i-lock"></use></svg></div>
                    <h2>Access PIN required</h2>
                    <p>The server on your phone requires a PIN. Enter it to browse the media library.</p>
                    <input type="password" id="loginPin" inputmode="numeric" maxlength="6" autocomplete="off" aria-label="Enter access PIN" placeholder="&#9679;&#9679;&#9679;&#9679;">
                    <button class="glass-btn glass-btn--accent glass-btn--lg" id="loginBtn">Unlock</button>
                    <div class="login-err" id="loginErr"></div>
                    <span class="login-hint">Set or clear the PIN in the LocalStream app under Settings.</span>
                </div>
            </div>

            <div class="toast" id="toast" role="alert" aria-live="assertive"></div>

            <noscript><p style="text-align:center;padding:40px;color:#8b949e">Enable JavaScript to browse the media library.</p></noscript>

            <script>
            'use strict';
            (function(){
                var items = [];
                var folders = [];
                var tab = 'all';
                var query = '';
                var sortKey = 'name';
                var connState = 'checking';
                var connFails = 0;
                var connTimer = null;
                var playerOpen = false;
                var controlsTimer = null;
                var lastTouch = 0;
                var lastTapX = 0;
                var currentFolder = null;
                var folderStack = [];
                var loginShown = false;
                var eventsSrc = null;
                var authToken = null;   // returned by /api/v1/auth/verify; used for shareable/VLC URLs
                var retryingAt = 0;     // timestamp guard: ignore transient errors right after a manual reload

                var $ = function(id){ return document.getElementById(id); };
                var library = $('library');
                var emptyBox = $('empty');
                var count = $('count');
                var search = $('search');
                var sort = $('sort');
                var connDot = $('connDot');
                var connText = $('connText');
                var player = $('player');
                var stage = $('stage');
                var cover = $('cover');
                var spinner = $('spinner');
                var perror = $('perror');
                var controls = $('controls');
                var mediaEl = null;
                var mediaMeta = null;
                var mediaType = 'video';
                var demuxRetries = 0;

                function escapeHtml(text){
                    return String(text)
                        .replace(/&/g, '&amp;')
                        .replace(/</g, '&lt;')
                        .replace(/>/g, '&gt;')
                        .replace(/"/g, '&quot;')
                        .replace(/'/g, '&#39;');
                }

                function formatBytes(bytes){
                    if (bytes == null || !isFinite(bytes) || bytes < 0) return '';
                    if (bytes === 0) return '0 B';
                    var units = ['B','KB','MB','GB','TB'];
                    var i = Math.floor(Math.log(bytes) / Math.log(1024));
                    if (i >= units.length) i = units.length - 1;
                    var value = bytes / Math.pow(1024, i);
                    var label = (i === 0) ? value.toFixed(0) : (value >= 100 ? value.toFixed(0) : value.toFixed(1));
                    return label + ' ' + units[i];
                }

                function typeIcon(type){
                    if (type === 'video') return '#i-film';
                    if (type === 'audio') return '#i-music';
                    if (type === 'image') return '#i-image';
                    return '#i-file';
                }
                function typeLabel(type){
                    if (type === 'video') return 'Video';
                    if (type === 'audio') return 'Audio';
                    if (type === 'image') return 'Image';
                    if (type) return type;
                    return 'File';
                }
                function typeClass(type){
                    if (type === 'video' || type === 'audio' || type === 'image') return type;
                    return 'other';
                }
                function mimeText(mime){
                    if (!mime) return '';
                    var parts = String(mime).split('/');
                    return parts.length === 2 && parts[1] ? parts[1].toUpperCase() : mime;
                }

                function fmtClock(ms){
                    var total = Math.floor((Number(ms) || 0) / 1000);
                    if (total <= 0) return '';
                    var h = Math.floor(total / 3600);
                    var m = Math.floor((total % 3600) / 60);
                    var s = total % 60;
                    var mm = (h > 0 && m < 10) ? '0' + m : String(m);
                    var ss = s < 10 ? '0' + s : String(s);
                    return h > 0 ? h + ':' + mm + ':' + ss : m + ':' + ss;
                }

                function folderCardHtml(folder){
                    var id = encodeURIComponent(folder.id);
                    var count = (folder.itemCount > 0) ? (folder.itemCount + (folder.itemCount === 1 ? ' item' : ' items')) : 'Folder';
                    return '<button class="folder-card" data-folder="' + id + '" aria-label="Open folder ' + escapeHtml(folder.name) + '">'
                        + '<span class="folder-icon"><svg class="icon"><use href="#i-folder"></use></svg></span>'
                        + '<span style="min-width:0;display:flex;flex-direction:column">'
                        + '<span class="folder-name">' + escapeHtml(folder.name) + '</span>'
                        + '<span class="folder-count">' + count + '</span></span>'
                        + '</button>';
                }

                function cardHtml(item){
                    var id = encodeURIComponent(item.id);
                    var stream = '/api/v1/stream/' + id;
                    var icon = typeIcon(item.type);
                    var cls = typeClass(item.type);
                    var metaBits = [];
                    if (mimeText(item.mimeType)) metaBits.push(mimeText(item.mimeType));
                    var size = formatBytes(item.size);
                    if (size) metaBits.push(size);
                    var meta = metaBits.join(' \u00b7 ');
                    var title = item.name || 'Untitled';
                    var safeTitle = escapeHtml(title);
                    var unavailable = item.available === false;
                    var unav = unavailable ? '<span class="unav-tag">Unavailable</span>' : '';
                    var thumb = '';
                    if (item.thumbUrl && !unavailable){
                        thumb = '<img class="thumb" loading="lazy" decoding="async" src="' + item.thumbUrl + '" alt="" onerror="this.hidden=true">';
                    }
                    var dur = '';
                    var dms = Number(item.durationMs);
                    if (isFinite(dms) && dms > 0){
                        dur = '<span class="dur-badge">' + fmtClock(dms) + '</span>';
                    }
                    var foot = unavailable
                        ? '<div class="card-foot"><span class="glass-btn glass-btn--sm" disabled>Unavailable</span></div>'
                        : '<div class="card-foot">'
                            + '<button class="glass-btn glass-btn--sm glass-btn--accent" data-act="play" data-id="' + id + '" aria-label="Play ' + safeTitle + '"><svg class="icon"><use href="#i-play"></use></svg> Play</button>'
                            + '<a class="glass-btn glass-btn--sm glass-btn--icon" href="' + stream + '" download aria-label="Download ' + safeTitle + '"><svg class="icon"><use href="#i-download"></use></svg></a>'
                          + '</div>';
                    return '<article class="card' + (unavailable ? ' unavailable' : '') + '" tabindex="0" role="button" aria-label="' + safeTitle + ' — ' + typeLabel(item.type) + (size ? ', ' + size : '') + '" data-id="' + id + '">'
                        + '<div class="cover cover--' + cls + '">' + thumb
                        + '<span class="cover-tag">' + typeLabel(item.type) + '</span>' + unav + dur
                        + '<svg class="icon icon--big"><use href="' + icon + '"></use></svg></div>'
                        + '<div class="card-body"><h3 class="card-title">' + safeTitle + '</h3><p class="card-meta">' + meta + '</p></div>'
                        + foot
                        + '</article>';
                }

                function getIconUse(id){
                    var u = document.createElementNS('http://www.w3.org/2000/svg', 'use');
                    u.setAttribute('href', id);
                    return u;
                }

                function renderChrome(){
                    var fbar = $('folderBar');
                    var fpath = $('folderPath');
                    var fbox = $('folders');

                    fbox.innerHTML = folders.map(folderCardHtml).join('');
                    fbox.hidden = folders.length === 0;

                    if (currentFolder){
                        fbar.hidden = false;
                        var crumbs = [];
                        crumbs.push('<button class="crumb" data-crumb="root">Root</button>');
                        var segs = folderStack.slice();
                        segs.push(currentFolder);
                        for (var k = 0; k < segs.length; k++){
                            if (k < segs.length - 1){
                                crumbs.push('<span class="crumb-sep">/</span>');
                                crumbs.push('<span class="crumb">' + escapeHtml(segs[k].name) + '</span>');
                            } else {
                                crumbs.push('<span class="crumb-sep">/</span>');
                                crumbs.push('<span class="crumb-current">' + escapeHtml(segs[k].name) + '</span>');
                            }
                        }
                        fpath.innerHTML = crumbs.join('');
                    } else {
                        fbar.hidden = true;
                        fpath.innerHTML = '';
                    }
                }

                function render(){
                    renderChrome();
                    var visible = items.slice();
                    if (tab !== 'all') visible = visible.filter(function(it){ return it.type === tab; });
                    if (query) {
                        var q = query.toLowerCase();
                        visible = visible.filter(function(it){
                            return String(it.name || '').toLowerCase().indexOf(q) !== -1
                                || String(it.mimeType || '').toLowerCase().indexOf(q) !== -1
                                || String(it.type || '').toLowerCase().indexOf(q) !== -1;
                        });
                    }
                    if (sortKey === 'sizeDesc') visible.sort(function(a,b){ return (b.size||0) - (a.size||0); });
                    else if (sortKey === 'sizeAsc') visible.sort(function(a,b){ return (a.size||0) - (b.size||0); });
                    else if (sortKey === 'type') visible.sort(function(a,b){
                        var ta = a.type || ''; var tb = b.type || '';
                        return ta === tb ? String(a.name||'').localeCompare(String(b.name||'')) : ta.localeCompare(tb);
                    });
                    else visible.sort(function(a,b){ return String(a.name||'').localeCompare(String(b.name||''), undefined, {numeric:true, sensitivity:'base'}); });

                    if (items.length === 0 && folders.length === 0){
                        library.innerHTML = '';
                        emptyBox.hidden = false;
                        if (currentFolder){
                            emptyBox.innerHTML = '<h2>This folder is empty</h2><p>Nothing here yet. Go up to browse more of the library.</p>'
                                + '<button class="glass-btn glass-btn--accent" id="emptyUp"><svg class="icon"><use href="#i-back"></use></svg> Up one level</button>';
                            $('emptyUp').addEventListener('click', function(){ goUp(); });
                        } else {
                            emptyBox.innerHTML = '<h2>No media yet</h2><p>Add folders or share media from the LocalStream app on your phone, then refresh.</p>'
                                + '<button class="glass-btn glass-btn--accent" id="emptyRefresh"><svg class="icon"><use href="#i-refresh"></use></svg> Refresh library</button>';
                            $('emptyRefresh').addEventListener('click', function(){ loadLibrary(false); });
                        }
                        count.textContent = '0 items';
                        return;
                    }
                    emptyBox.hidden = true;
                    if (visible.length === 0 && folders.length === 0){
                        library.innerHTML = '';
                        count.textContent = '0 of ' + items.length;
                        emptyBox.hidden = false;
                        emptyBox.innerHTML = '<h2>Nothing here</h2><p>No media matches this filter' + (query ? ' or search for ' + escapeHtml(query) : '') + '.</p>';
                        return;
                    }
                    count.textContent = (tab !== 'all' || query) ? (visible.length + ' of ' + items.length) : (visible.length + ((visible.length === 1) ? ' item' : ' items'));
                    library.innerHTML = visible.map(cardHtml).join('');
                }

                function setGridLoading(){
                    var skeletons = '';
                    for (var i = 0; i < 8; i++){
                        skeletons += '<div class="skel"><div class="skel-cover"></div><div class="skel-lines"><div style="height:12px;border-radius:4px;background:#1f262f;width:80%"></div><div style="height:10px;border-radius:4px;background:#1a2029;width:55%"></div></div></div>';
                    }
                    library.innerHTML = skeletons;
                    count.textContent = '';
                    emptyBox.hidden = true;
                }

                function showToast(message){
                    var t = $('toast');
                    t.textContent = message;
                    t.classList.add('show');
                    clearTimeout(showToast._timer);
                    showToast._timer = setTimeout(function(){ t.classList.remove('show'); }, 4200);
                }

                function fetchJSON(url){
                    return fetch(url, { cache: 'no-store', credentials: 'same-origin' }).then(function(res){
                        if (res.status === 401){ showLogin(); throw new Error('auth'); }
                        if (!res.ok) throw new Error('HTTP ' + res.status);
                        return res.json();
                    });
                }

                function showLogin(){
                    if (loginShown) return;
                    loginShown = true;
                    $('login').hidden = false;
                    setTimeout(function(){
                        var input = $('loginPin');
                        if (input && document.activeElement !== input) input.focus();
                    }, 60);
                }
                function hideLogin(){
                    loginShown = false;
                    $('login').hidden = true;
                    $('loginErr').textContent = '';
                    $('loginPin').value = '';
                }
                function doLogin(){
                    var pin = $('loginPin').value.trim();
                    if (!pin) return;
                    $('loginBtn').disabled = true;
                    $('loginErr').textContent = '';
                    fetch('/api/v1/auth/verify', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        credentials: 'same-origin',
                        body: JSON.stringify({ pin: pin })
                    }).then(function(res){
                        if (res.status === 200){
                            return res.json().catch(function(){ return {}; }).then(function(data){
                                if (data && typeof data.token === 'string' && data.token){
                                    authToken = data.token;
                                }
                                hideLogin();
                                loadLibrary(false);
                                checkConn();
                            });
                        } else {
                            return res.json().catch(function(){ return {}; }).then(function(data){
                                throw new Error((data && data.error && data.error.message) || 'Incorrect PIN');
                            });
                        }
                    }).catch(function(err){
                        $('loginErr').textContent = (err && err.message && err.message !== 'auth')
                            ? err.message
                            : 'Could not verify the PIN. Try again.';
                    }).then(function(){
                        $('loginBtn').disabled = false;
                    });
                }

                function openFolder(folderId, folderName){
                    if (!folderId) return;
                    if (currentFolder) folderStack.push(currentFolder);
                    currentFolder = { id: folderId, name: folderName || 'Folder' };
                    resetTab('all');
                    loadLibrary(false, folderId);
                }
                function goUp(){
                    if (!currentFolder) return;
                    var parent = folderStack.pop() || null;
                    currentFolder = parent;
                    resetTab('all');
                    loadLibrary(false, parent ? parent.id : null);
                }
                function goRoot(){
                    folderStack = [];
                    currentFolder = null;
                    resetTab('all');
                    loadLibrary(false, null);
                }
                function resetTab(which){
                    tab = which;
                    var tabs = document.querySelectorAll('#tabs .tab');
                    for (var i = 0; i < tabs.length; i++){
                        tabs[i].classList.toggle('active', tabs[i].getAttribute('data-tab') === which);
                    }
                }

                function loadLibrary(silent, folderId){
                    if (!silent) setGridLoading();
                    var pending = folderId || (currentFolder ? currentFolder.id : null);
                    var url = '/api/v1/files';
                    if (pending) url += '?parent=' + encodeURIComponent(pending);
                    fetch(url, { cache: 'no-store', credentials: 'same-origin' }).then(function(res){
                        if (res.status === 401){ showLogin(); throw new Error('auth'); }
                        if (!res.ok) throw new Error('HTTP ' + res.status);
                        return res.json();
                    }).then(function(data){
                        if (pending){
                            items = Array.isArray(data.items) ? data.items : [];
                            folders = Array.isArray(data.folders) ? data.folders : [];
                        } else {
                            items = Array.isArray(data.items) ? data.items : [];
                            return fetchJSON('/api/v1/folders').then(function(fd){
                                folders = (fd && Array.isArray(fd.folders)) ? fd.folders : [];
                            }).catch(function(){ folders = []; });
                        }
                    }).catch(function(err){
                        if (err && err.message === 'auth') return;
                        if (!silent){
                            emptyBox.hidden = false;
                            emptyBox.innerHTML = '<h2>Unable to load the media library</h2><p>Check that the LocalStream server is running, then retry.</p>'
                                + '<button class="glass-btn glass-btn--accent" id="emptyRetry"><svg class="icon"><use href="#i-retry"></use></svg> Retry</button>';
                            $('emptyRetry').addEventListener('click', function(){ loadLibrary(false); });
                        }
                    }).then(function(){
                        render();
                        if (watchId && !pending){ openWatch(watchId); }
                    });
                }

                /* Connection monitor (polling with exponential backoff) */
                var CONN_BASE = 5000;
                var CONN_MAX = 25000;
                var wasOffline = false;

                function setConn(state){
                    connState = state;
                    connDot.className = 'conn-dot ' + state;
                    if (state === 'connected') connText.textContent = 'Connected';
                    else if (state === 'reconnecting') connText.textContent = 'Reconnecting…';
                    else if (state === 'offline') connText.textContent = 'Server unavailable';
                    else connText.textContent = 'Checking…';
                }

                function checkConn(){
                    fetch('/api/v1/status', { cache: 'no-store' }).then(function(res){
                        if (res.status === 401){ showLogin(); throw new Error('auth'); }
                        if (!res.ok) throw new Error('bad status');
                        return res.json();
                    }).then(function(){
                        if (connState !== 'connected'){
                            if (wasOffline) loadLibrary(true);
                            wasOffline = false;
                        }
                        connFails = 0;
                        setConn('connected');
                        scheduleConn(CONN_BASE);
                    }).catch(function(){
                        connFails++;
                        if (connFails >= 3){ wasOffline = true; setConn('offline'); }
                        else setConn('reconnecting');
                        var delay = Math.min(CONN_BASE * Math.pow(2, Math.min(connFails, 3)), CONN_MAX);
                        scheduleConn(delay);
                    });
                }
                function scheduleConn(delay){
                    clearTimeout(connTimer);
                    connTimer = setTimeout(checkConn, delay);
                }

                /* Player */
                function openPlayer(item){
                    if (playerOpen) closePlayer();
                    mediaMeta = item;
                    demuxRetries = 0;
                    retryingAt = 0;
                    var id = encodeURIComponent(item.id);
                    mediaType = (item.type === 'video' || item.type === 'audio') ? item.type : 'video';
                    document.title = (item.name || 'Media') + ' — LocalStream';
                    $('ptitle').textContent = item.name || 'Media';
                    $('pLink').setAttribute('href', '/watch/' + id);
                    $('pDownload').setAttribute('href', '/api/v1/stream/' + id);

                    var old = stage.querySelector('video, audio');
                    if (old) old.remove();
                    mediaEl = document.createElement(mediaType);
                    mediaEl.preload = 'metadata';
                    if (mediaType === 'video') mediaEl.playsInline = true;
                    if (item.mimeType) mediaEl.setAttribute('type', item.mimeType);
                    mediaEl.src = '/api/v1/stream/' + id;
                    stage.insertBefore(mediaEl, cover);

                    bindMediaEvents();
                    resetControlsUI();

                    if (item.subtitles && item.subtitles.length){
                        populateSubMenu(item.subtitles);
                        $('cSub').hidden = false;
                        $('subMenu').classList.remove('open');
                        setSubtitle(0);
                    } else {
                        $('cSub').hidden = true;
                        $('subMenu').classList.remove('open');
                    }
                    if (Number(item.durationMs) > 0){
                        $('cTime').textContent = '0:00 / ' + fmtClock(item.durationMs);
                    }

                    player.hidden = false;
                    playerOpen = true;
                    document.body.classList.add('no-scroll');
                    showControls();
                    mediaEl.load();
                }

                function openWatch(id){
                    var realId = null;
                    try { realId = decodeURIComponent(id); } catch(e){ realId = id; }
                    var fromList = null;
                    for (var i = 0; i < items.length; i++){
                        if (items[i].id === realId){ fromList = items[i]; break; }
                    }
                    if (fromList){ openPlayer(fromList); return; }
                    fetchJSON('/api/v1/files/' + realId).then(openPlayer).catch(function(){
                        showToast('Media not found on this server.');
                    });
                }

                function closePlayer(){
                    if (mediaEl){
                        mediaEl.pause();
                        mediaEl.removeAttribute('src');
                        mediaEl.load();
                        mediaEl = null;
                    }
                    mediaMeta = null;
                    playerOpen = false;
                    player.hidden = true;
                    document.body.classList.remove('no-scroll');
                    document.title = 'LocalStream Media';
                    clearTimeout(controlsTimer);
                    $('pLink').removeAttribute('href');
                    $('subMenu').classList.remove('open');
                }

                function populateSubMenu(tracks){
                    var menu = $('subMenu');
                    menu.innerHTML = '';
                    function add(label, idx){
                        var b = document.createElement('button');
                        b.textContent = label;
                        b.type = 'button';
                        b.setAttribute('role', 'menuitem');
                        b.addEventListener('click', function(){
                            setSubtitle(idx);
                            menu.classList.remove('open');
                        });
                        menu.appendChild(b);
                    }
                    add('Off', -1);
                    for (var i = 0; i < tracks.length; i++){
                        add(tracks[i].name || ('Subtitle ' + (i + 1)), i);
                    }
                }
                function setSubtitle(idx){
                    if (!mediaEl || !mediaEl.textTracks) return;
                    for (var i = 0; i < mediaEl.textTracks.length; i++){
                        mediaEl.textTracks[i].mode = (idx === i) ? 'showing' : 'hidden';
                    }
                    var btns = $('subMenu').querySelectorAll('button');
                    for (var j = 0; j < btns.length; j++){
                        btns[j].classList.toggle('active', (j - 1) === idx);
                    }
                }

                function resetControlsUI(){
                    $('perror').hidden = true;
                    spinner.hidden = true;
                    cover.hidden = false;
                    $('coverHint').textContent = mediaType === 'video'
                        ? 'Tap play to start streaming — only the range you need is transferred.'
                        : 'Tap play to start streaming this audio file.';
                    $('cTime').textContent = '0:00 / 0:00';
                    $('cBuffer').style.width = '0%';
                    $('cSeek').value = 0;
                    setPlayIcon(false);
                    $('cVol').value = 1;
                    setMuteIcon(false);
                }

                function bindMediaEvents(){
                    mediaEl.addEventListener('waiting', function(){ spinner.hidden = false; });
                    mediaEl.addEventListener('stalled', function(){ spinner.hidden = false; });
                    mediaEl.addEventListener('playing', function(){
                        spinner.hidden = true;
                        cover.hidden = true;
                        $('perror').hidden = true;
                        retryingAt = 0;
                    });
                    mediaEl.addEventListener('canplay', function(){ spawnHideControls(); });
                    mediaEl.addEventListener('play', function(){ setPlayIcon(true); });
                    mediaEl.addEventListener('pause', function(){ setPlayIcon(false); showControls(); });
                    mediaEl.addEventListener('ended', function(){
                        setPlayIcon(false);
                        showControls();
                        $('coverHint').textContent = 'Playback finished.';
                        cover.hidden = false;
                        cover.querySelector('button').focus();
                    });
                    mediaEl.addEventListener('timeupdate', updateTime);
                    mediaEl.addEventListener('loadedmetadata', updateTime);
                    mediaEl.addEventListener('progress', updateBuffer);
                    mediaEl.addEventListener('durationchange', updateTime);
                    mediaEl.addEventListener('error', onMediaError);
                }

                function onMediaError(){
                    if (Date.now() - retryingAt < 4000){ return; }
                    var el = mediaEl;
                    // If playback is actually progressing (audio keeps playing),
                    // the erroring resource was auxiliary — don't block the UI.
                    if (el && !el.paused && el.readyState >= 2 && isFinite(el.currentTime) && el.currentTime > 0){
                        $('perror').hidden = true;
                        cover.hidden = true;
                        spinner.hidden = true;
                        return;
                    }
                    spinner.hidden = true;
                    cover.hidden = true;
                    var code = el && el.error ? el.error.code : 0;
                    var detail = el && el.error && el.error.message ? el.error.message : '';
                    var isDemux = /DEMUXER|FFmpegDemuxer|PIPELINE|CODEC/i.test(detail);
                    var vlcHint = ' Tap the VLC button to copy the network stream URL and play it in VLC (Ctrl+N).';
                    var msg;
                    if (isDemux){
                        msg = 'This browser could not open or decode this file. Its container or audio/video codec (e.g. DTS, TrueHD, AC-3) may not be supported in the browser.' + vlcHint;
                    } else if (code === 4){
                        msg = 'Unable to play this media in your browser. The stream may use a video or audio codec that this browser does not support.' + vlcHint;
                    } else if (code === 2){
                        msg = 'The network connection was interrupted.';
                    } else {
                        msg = 'Unable to play this video. Try another browser or media file.' + vlcHint;
                    }
                    $('perrorText').textContent = msg;
                    $('perrorCode').textContent = detail ? detail : (code ? 'Error ' + code : '');
                    $('perror').hidden = false;
                    if (isDemux && demuxRetries < 1){
                        demuxRetries++;
                        setTimeout(retryPlayback, 600);
                    }
                }

                function retryPlayback(){
                    if (!mediaEl) return;
                    $('perror').hidden = true;
                    cover.hidden = true;
                    spinner.hidden = false;
                    retryingAt = Date.now();
                    var src = mediaEl.getAttribute('src');
                    var mime = mediaEl.getAttribute('type');
                    mediaEl.removeAttribute('src');
                    mediaEl.load();
                    mediaEl.setAttribute('src', src);
                    if (mime) mediaEl.setAttribute('type', mime);
                    mediaEl.play().catch(function(err){
                        spinner.hidden = true;
                        retryingAt = 0;
                        if (err && err.name !== 'AbortError'){
                            $('perrorText').textContent = 'Still unable to play this media in this browser. Try the VLC button to stream it externally.';
                            $('perrorCode').textContent = err ? String(err.name) : '';
                            $('perror').hidden = false;
                            probeTranscode(mediaMeta);
                        }
                    });
                    // Safety net: if the reload never plays, surface the error again.
                    setTimeout(function(){
                        if (Date.now() - retryingAt >= 4000){
                            retryingAt = 0;
                            if (mediaEl && mediaEl.paused && (!isFinite(mediaEl.currentTime) || mediaEl.currentTime === 0)){
                                $('perrorText').textContent = 'Still unable to play this media in this browser. Try the VLC button to stream it externally.';
                                $('perrorCode').textContent = '';
                                $('perror').hidden = false;
                                probeTranscode(mediaMeta);
                            }
                        }
                    }, 4200);
                }

                function probeTranscode(item){
                    if (!item || !item.id) return;
                    fetch('/api/v1/transcode/' + encodeURIComponent(item.id), {
                        cache: 'no-store',
                        credentials: 'same-origin'
                    }).then(function(res){
                        if (res.status === 501){
                            var note = 'Server-side transcoding is not configured on this server.';
                            var cur = $('perrorCode').textContent || '';
                            $('perrorCode').textContent = cur ? (cur + ' \u00b7 ' + note) : note;
                        }
                    }).catch(function(){});
                }

                function copyStreamUrl(){
                    if (!mediaMeta || !mediaMeta.id) return;
                    var url = location.origin + '/api/v1/stream/' + encodeURIComponent(mediaMeta.id);
                    if (authToken){
                        url += (url.indexOf('?') === -1 ? '?' : '&') + 'token=' + encodeURIComponent(authToken);
                    }
                    var done = function(){
                        showToast('Stream URL copied. In VLC press Ctrl+N (or Media > Open Network Stream) and paste it.');
                    };
                    var fallback = function(){
                        var ta = document.createElement('textarea');
                        ta.value = url;
                        ta.setAttribute('readonly', '');
                        ta.style.position = 'fixed';
                        ta.style.left = '-9999px';
                        document.body.appendChild(ta);
                        ta.select();
                        try {
                            document.execCommand('copy');
                            done();
                        } catch(e){
                            showToast('Copy failed. Open the app in a modern browser to copy the VLC link.');
                        }
                        document.body.removeChild(ta);
                    };
                    if (navigator.clipboard && navigator.clipboard.writeText){
                        navigator.clipboard.writeText(url).then(done, fallback);
                    } else {
                        fallback();
                    }
                }

                function togglePlay(){
                    if (!mediaEl) return;
                    if (mediaEl.paused){
                        mediaEl.play().catch(function(err){
                            if (err && err.name === 'NotAllowedError'){
                                $('perrorText').textContent = 'Playback did not start automatically. Tap Play again to begin.';
                                $('perrorCode').textContent = '';
                                $('perror').hidden = false;
                            } else {
                                $('perrorText').textContent = 'Unable to play this video. Try another browser or media file.';
                                $('perrorCode').textContent = err ? String(err.name) : '';
                                $('perror').hidden = false;
                            }
                        });
                    } else {
                        mediaEl.pause();
                    }
                }

                function seekBy(delta){
                    if (mediaEl && isFinite(mediaEl.duration)){
                        mediaEl.currentTime = Math.max(0, Math.min(mediaEl.duration, mediaEl.currentTime + delta));
                    }
                }

                function updateTime(){
                    if (!mediaEl) return;
                    var d = isFinite(mediaEl.duration) ? mediaEl.duration : 0;
                    var c = isFinite(mediaEl.currentTime) ? mediaEl.currentTime : 0;
                    $('cTime').textContent = fmtTime(c) + ' / ' + fmtTime(d);
                    if (document.activeElement !== $('cSeek')){
                        var pct = d > 0 ? (c / d) : 0;
                        $('cSeek').value = Math.round(pct * 1000);
                    }
                }

                function updateBuffer(){
                    if (!mediaEl || !mediaEl.buffered || !mediaEl.duration) return;
                    var d = mediaEl.duration;
                    var end = 0;
                    for (var i = 0; i < mediaEl.buffered.length; i++){
                        if (mediaEl.buffered.end(i) > end) end = mediaEl.buffered.end(i);
                    }
                    $('cBuffer').style.width = (d > 0 ? (Math.min(end, d) / d) * 100 : 0) + '%';
                }

                function fmtTime(seconds){
                    if (!isFinite(seconds) || seconds < 0) return '0:00';
                    var total = Math.floor(seconds);
                    var h = Math.floor(total / 3600);
                    var m = Math.floor((total % 3600) / 60);
                    var s = total % 60;
                    var base = m + ':' + (s < 10 ? '0' : '') + s;
                    return h > 0 ? h + ':' + (m < 10 ? '0' : '') + base : base;
                }

                function setPlayIcon(playing){
                    var icon = $('cPlayIcon').firstElementChild;
                    icon.setAttribute('href', playing ? '#i-pause' : '#i-play');
                    $('cPlay').setAttribute('aria-label', playing ? 'Pause' : 'Play');
                }
                function setMuteIcon(muted){
                    var icon = $('cMuteIcon').firstElementChild;
                    icon.setAttribute('href', muted ? '#i-voloff' : '#i-vol');
                    $('cMute').setAttribute('aria-label', muted ? 'Unmute' : 'Mute');
                }
                function updateVolumeUI(){
                    if (!mediaEl) return;
                    $('cVol').value = mediaEl.volume;
                    setMuteIcon(mediaEl.muted || mediaEl.volume === 0);
                }

                function showControls(){
                    controls.classList.remove('controls-hidden');
                    clearTimeout(controlsTimer);
                    if (!mediaEl || mediaEl.paused || mediaEl.ended) return;
                    controlsTimer = setTimeout(function(){ controls.classList.add('controls-hidden'); }, 3200);
                }
                function spawnHideControls(){
                    controls.classList.remove('controls-hidden');
                    clearTimeout(controlsTimer);
                    controlsTimer = setTimeout(function(){
                        if (mediaEl && !mediaEl.paused && !mediaEl.ended){
                            controls.classList.add('controls-hidden');
                        }
                    }, 3200);
                }

                function toggleFullscreen(){
                    if (document.fullscreenElement || document.webkitFullscreenElement){
                        if (document.exitFullscreen) document.exitFullscreen();
                        else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
                    } else if (stage.requestFullscreen){
                        stage.requestFullscreen().catch(function(){});
                    } else if (stage.webkitRequestFullscreen){
                        stage.webkitRequestFullscreen();
                    }
                }

                function onFullscreenChange(){
                    var on = !!(document.fullscreenElement || document.webkitFullscreenElement);
                    var icon = $('cFullIcon').firstElementChild;
                    icon.setAttribute('href', on ? '#i-exit' : '#i-full');
                    $('cFull').setAttribute('aria-label', on ? 'Exit fullscreen' : 'Fullscreen');
                }

                function wirePlayer(){
                    $('coverPlay').addEventListener('click', function(){ cover.hidden = true; togglePlay(); });
                    $('pClose').addEventListener('click', closePlayer);
                    $('cPlay').addEventListener('click', togglePlay);
                    $('cMute').addEventListener('click', function(){
                        if (!mediaEl) return;
                        mediaEl.muted = !mediaEl.muted;
                        updateVolumeUI();
                    });
                    $('cVol').addEventListener('input', function(){
                        if (!mediaEl) return;
                        mediaEl.volume = parseFloat($('cVol').value);
                        mediaEl.muted = mediaEl.volume === 0;
                        setMuteIcon(mediaEl.muted);
                    });
                    $('cSeek').addEventListener('input', function(){
                        if (!mediaEl || !isFinite(mediaEl.duration) || !mediaEl.duration) return;
                        var pct = parseInt($('cSeek').value, 10) / 1000;
                        mediaEl.currentTime = pct * mediaEl.duration;
                    });
                    $('cFull').addEventListener('click', toggleFullscreen);
                    $('pRetry').addEventListener('click', function(){
                        retryPlayback();
                    });
                    var vlcBtn = $('pVlc');
                    if (vlcBtn) vlcBtn.addEventListener('click', copyStreamUrl);
                    var vlcErrBtn = $('pVlcErr');
                    if (vlcErrBtn) vlcErrBtn.addEventListener('click', copyStreamUrl);
                    $('cSub').addEventListener('click', function(){
                        var menu = $('subMenu');
                        if (menu.classList.contains('open')) menu.classList.remove('open');
                        else menu.classList.add('open');
                    });
                    document.addEventListener('fullscreenchange', onFullscreenChange);
                    document.addEventListener('webkitfullscreenchange', onFullscreenChange);

                    stage.addEventListener('click', function(e){
                        $('subMenu').classList.remove('open');
                        if (e.target.closest && e.target.closest('button, input, a')) return;
                        showControls();
                    });
                    stage.addEventListener('dblclick', function(e){
                        seekBy(e.clientX < window.innerWidth / 2 ? -10 : 10);
                    });
                    stage.addEventListener('touchstart', function(e){
                        var now = Date.now();
                        if (now - lastTouch < 300){
                            seekBy(e.touches[0].clientX < window.innerWidth / 2 ? -10 : 10);
                            return;
                        }
                        lastTouch = now;
                        lastTapX = e.touches[0].clientX;
                    }, { passive: true });
                    stage.addEventListener('touchend', function(e){
                        var now = Date.now();
                        if (now - lastTouch < 300){ return; }
                        lastTouch = now;
                    }, { passive: true });

                    document.addEventListener('keydown', function(e){
                        if (!playerOpen || !mediaEl) return;
                        if (e.key === 'Escape'){ closePlayer(); e.preventDefault(); return; }
                        var tag = (e.target && e.target.tagName) ? e.target.tagName : '';
                        var isInput = tag === 'INPUT' || tag === 'SELECT' || tag === 'TEXTAREA';
                        if ((e.key === ' ' || e.key === 'k') && !isInput){
                            togglePlay(); e.preventDefault();
                        } else if (e.key === 'ArrowLeft'){
                            seekBy(-10); e.preventDefault();
                        } else if (e.key === 'ArrowRight'){
                            seekBy(10); e.preventDefault();
                        } else if (e.key === 'ArrowUp'){
                            if (mediaEl){ mediaEl.volume = Math.min(1, (mediaEl.muted ? 0 : mediaEl.volume) + 0.1); mediaEl.muted = false; updateVolumeUI(); }
                            e.preventDefault();
                        } else if (e.key === 'ArrowDown'){
                            if (mediaEl){ mediaEl.volume = Math.max(0, mediaEl.volume - 0.1); updateVolumeUI(); }
                            e.preventDefault();
                        } else if (e.key === 'f'){
                            toggleFullscreen(); e.preventDefault();
                        } else if (e.key === 'm'){
                            if (mediaEl){ mediaEl.muted = !mediaEl.muted; updateVolumeUI(); }
                            e.preventDefault();
                        }
                    });

                    var hideTimer = null;
                    player.addEventListener('mousemove', function(){ spawnHideControls(); });
                }

                /* Library interactions */
                function openCard(card){
                    var id = card.getAttribute('data-id');
                    if (!id) return;
                    if (card.classList.contains('unavailable')) return;
                    var item = null;
                    for (var i = 0; i < items.length; i++){
                        if (encodeURIComponent(items[i].id) === id){ item = items[i]; break; }
                    }
                    openPlayer(item || { id: decodeURIComponent(id) });
                }
                function handleGridClick(e){
                    if (e.target.closest && e.target.closest('a[download]')) return;
                    var card = e.target.closest ? e.target.closest('.card') : null;
                    if (!card) return;
                    openCard(card);
                }
                library.addEventListener('click', handleGridClick);
                library.addEventListener('keydown', function(e){
                    if (e.key !== 'Enter' && e.key !== ' ') return;
                    var t = e.target;
                    if (!t || !t.classList || !t.classList.contains('card')) return;
                    e.preventDefault();
                    openCard(t);
                });

                var watchId = document.body.getAttribute('data-watch-id') || '';
                if (watchId){
                    try { watchId = decodeURIComponent(watchId); } catch(e){}
                }

                function connectEvents(){
                    if (!window.EventSource || eventsSrc) return;
                    try {
                        eventsSrc = new EventSource('/api/v1/events');
                        eventsSrc.addEventListener('library', function(){
                            loadLibrary(true);
                        });
                        eventsSrc.addEventListener('import', function(ev){
                            try {
                                var d = JSON.parse(ev.data);
                                var nameShown = (d && d.name) ? d.name : '';
                                if (d && d.done){
                                    showToast('Import finished: ' + nameShown);
                                } else if (d && d.error){
                                    showToast('Import failed: ' + nameShown);
                                } else {
                                    var total = Number(d && d.totalBytes) || 0;
                                    var bytes = Number(d && d.bytes) || 0;
                                    var pct = total > 0 ? Math.round(bytes / total * 100) + '%' : formatBytes(bytes);
                                    showToast('Importing ' + nameShown + ' \u00b7 ' + pct);
                                }
                            } catch(e){}
                        });
                        eventsSrc.onopen = function(){
                            if (connState === 'offline'){
                                wasOffline = true;
                                loadLibrary(true);
                            }
                        };
                        eventsSrc.onerror = function(){
                            // EventSource auto-reconnects; the poller drives the connection pill.
                        };
                    } catch(e){}
                }

                function init(){
                    var tabs = document.querySelectorAll('#tabs .tab');
                    for (var i = 0; i < tabs.length; i++){
                        tabs[i].addEventListener('click', function(){
                            for (var j = 0; j < tabs.length; j++) tabs[j].classList.remove('active');
                            this.classList.add('active');
                            tab = this.getAttribute('data-tab');
                            render();
                        });
                    }
                    search.addEventListener('input', function(){
                        query = search.value.trim();
                        render();
                    });
                    sort.addEventListener('change', function(){ sortKey = sort.value; render(); });
                    $('refresh').addEventListener('click', function(){ loadLibrary(false); });
                    window.addEventListener('online', function(){ loadLibrary(true); });

                    $('folderUp').addEventListener('click', goUp);
                    $('folderPath').addEventListener('click', function(e){
                        var crumb = e.target.closest ? e.target.closest('.crumb') : null;
                        if (crumb && crumb.getAttribute('data-crumb') === 'root') goRoot();
                    });
                    $('folders').addEventListener('click', function(e){
                        var fc = e.target.closest ? e.target.closest('.folder-card') : null;
                        if (!fc) return;
                        var fnameEl = fc.querySelector('.folder-name');
                        openFolder(fc.getAttribute('data-folder'), fnameEl ? fnameEl.textContent : 'Folder');
                    });
                    $('loginBtn').addEventListener('click', doLogin);
                    $('loginPin').addEventListener('keydown', function(e){
                        if (e.key === 'Enter'){
                            doLogin();
                            e.preventDefault();
                        }
                    });

                    wirePlayer();
                    connectEvents();
                    loadLibrary(false);
                    setConn('checking');
                    checkConn();
                }

                if (document.readyState === 'loading'){
                    document.addEventListener('DOMContentLoaded', init);
                } else {
                    init();
                }
            })();
            </script>
            </body>
            </html>
        """.trimIndent()
    }
}