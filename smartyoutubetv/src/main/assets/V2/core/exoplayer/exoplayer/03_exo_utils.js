console.log("Scripts::Running core script exo_utils.js");

/**
 * NOTE: object is used through the java code<br/>
 * NOTE: if you intend to rename/delete this var don't forget to do the same inside<br/>
 * <b>GetButtonStatesCommand.java</b> and <b>SyncButtonsCommand.java</b><br/>
 */
window.ExoUtils = {
    TAG: 'ExoUtils',
    ACTION_CLOSE_SUGGESTIONS: "action_close_suggestions",
    ACTION_PLAYBACK_STARTED: "action_playback_started",
    ACTION_DISABLE_KEY_EVENTS: "action_disable_key_events",

    // events order:
    // emptied
    // play
    // loadstart
    // loadedmetadata
    // loadeddata (first frame of the video has been loaded)
    // playing
    preparePlayer: function() {
        var $this = this;
        var player = Utils.$('video');
        var onPlayDelayMS = 2000;
        var onLoadDelayMS = 1000;

        if (!player || player.preparePlayerDone)
            return;

        // we can't pause video because history will not work
        function onLoad() {
            Log.d($this.TAG, 'preparePlayer: video has been loaded into webview... force start playback');
            setTimeout(function() {
                player.paused && player.play();
            }, onLoadDelayMS);
        }

        function onPlaying() {
            setTimeout(function() {
                Log.d($this.TAG, "preparePlayer: oops, video not paused yet... doing pause...");
                //$this.sendAction(ExoUtils.ACTION_PLAYBACK_STARTED);
                player.pause(); // prevent background playback
            }, onPlayDelayMS);
        }

        // once player is created it will be reused by other videos
        // 'loadeddata' is first event when video can be muted
        //player.addEventListener(DefaultEvents.PLAYER_DATA_LOADED, onLoad, false);
        player.addEventListener(DefaultEvents.PLAYER_PLAYING, onPlaying, false);

        // Utils.overrideProp(player, 'volume', 0);

        player.preparePlayerDone = true;
    },

    /**
     * Used when calling through app boundaries.
     */
    getButtonStates: function() {
        Log.d(this.TAG, "getButtonStates call time ms: " + Utils.getCurrentTimeMs());

        this.getButtonStatesTime = Utils.getCurrentTimeMs();

        // 10 second window between 'get' and 'sync'
        if (this.syncButtonsTime && (this.getButtonStatesTime - this.syncButtonsTime) < 10000) {
            return;
        }

        YouTubePlayerUtils.hidePlayerBackground();
        YouTubePlayerUtils.disablePlayerSuggestions();
        //this.preparePlayer();
        new SuggestionsWatcher(null); // init watcher

        var states = {};

        // NOTE: we can't delay here so process in reverse order
        var reversedKeys = Object.keys(PlayerActivityMapping).reverse();

        for (var idx in reversedKeys) {
            var key = reversedKeys[idx];
            var selector = PlayerActivityMapping[key];
            var btn = ExoButton.fromSelector(selector);
            var newName = PlayerActivity[key];
            var isChecked = btn.getChecked();
            if (isChecked === null) // exclude disabled buttons from result
                continue;
            states[newName] = isChecked;
        }
        
        states[PlayerActivity.SCREEN_WIDTH] = DeviceUtils.getScreenWidth();

        // don't let app to close video player (see ActionsReceiver.java)
        if (window.lastButtonName && window.lastButtonName == PlayerActivity.TRACK_ENDED) {
            states[PlayerActivity.BUTTON_NEXT] = null;
        }

        if (YouTubePlayerUtils.isPlayerClosed()) {
            YouTubePlayerUtils.showPlayerBackground();
        }

        Log.d(this.TAG, "getButtonStates: " + JSON.stringify(states));

        return states;
    },

    resetPlayerUI: function(callback, tries) {
        var $this = this;

        SuggestionsWatcher.disable();
        OverlayWatcher.disable();

        if (typeof tries === 'undefined') { // main entry point
            tries = 4;
        }

        if (tries > 0) {
            if (YouTubePlayerUtils.isAllPlayerUIClosed()) {
                Log.d(this.TAG, "Player's ui closed. Running callback...");

                clearTimeout(this.resetPlayerUITimeout);
                callback();
            } else {
                Log.d(this.TAG, "Player's ui NOT closed. Doing retry...");

                var activeElement = document.activeElement;

                EventUtils.triggerEvent(activeElement, DefaultEvents.KEY_DOWN, DefaultKeys.ESC);
                EventUtils.triggerEvent(activeElement, DefaultEvents.KEY_UP, DefaultKeys.ESC);

                this.resetPlayerUITimeout = setTimeout(function() {
                    $this.resetPlayerUI(callback, tries--);
                }, 300)
            }
        } else {
            Log.d(this.TAG, "Player's ui NOT closed and retries are out. Running callback anyway...");

            clearTimeout(this.resetPlayerUITimeout);
            callback();
        }
    },

    /**
     * Used when calling through app boundaries.
     */
    syncButtons: function(states) {
        Log.d(this.TAG, "syncButtons call time ms: " + Utils.getCurrentTimeMs());

        this.syncButtonsTime = Utils.getCurrentTimeMs();

        // 10 second window between 'get' and 'sync'
        if (this.getButtonStatesTime && (this.syncButtonsTime - this.getButtonStatesTime) < 10000) {
            return;
        }

        var $this = this;

        // // reset ui state (needed for button decorator)
        // this.resetPlayerUI(function() {
        //     $this.syncButtonsReal(states);
        // });

        SuggestionsWatcher.disable();
        OverlayWatcher.disable();
        YouTubePlayerUtils.resetPlayerOptions();
        // WARN: don't reset focus here!!!
        //YouTubeUtils.resetFocus();

        this.syncButtonsReal(states);

        // // 'likes not saved' fix
        // setTimeout(function() {
        //     $this.syncButtonsReal(states);
        // }, 100);
    },

    syncButtonsReal: function(states) {
        new SuggestionsWatcher(null); // init watcher

        window.lastButtonName = null;

        Log.d(this.TAG, "syncButtons: " + JSON.stringify(states));

        YouTubeUtils.sExoPlayerOpen = false;

        for (var key in PlayerActivity) {
            var btnId = PlayerActivity[key];
            var isChecked = states[btnId];
            if (isChecked == undefined) { // button gone, removed etc..
                continue;
            }

            if (btnId == PlayerActivity.BUTTON_SUGGESTIONS ||
                btnId == PlayerActivity.BUTTON_FAVORITES ||
                btnId == PlayerActivity.BUTTON_USER_PAGE) {
                // assume that exo is opened
                if (isChecked) {
                    YouTubeUtils.sExoPlayerOpen = true;
                }
            } else {
                this.cleanupOnClose();
            }

            var selector = PlayerActivityMapping[key];
            var btn = ExoButton.fromSelector(selector, states);
            btn.setChecked(isChecked);
        }
    },

    cleanupOnClose: function() {
        SuggestionsWatcher.disable();
        OverlayWatcher.disable();
        YouTubePlayerUtils.closePlayerControls();
        YouTubePlayerUtils.resetPlayerOptions();
        YouTubeUtils.resetFocus();
    },

    sendAction: function(action) {
        // code that sends string constant to activity
        if (app && app.onGenericStringResult) {
            Log.d(this.TAG, "sending action to the main app: " + action);

            // fix 'pause' when using favorites
            YouTubeUtils.sExoPlayerOpen = false;

            if (action == this.ACTION_CLOSE_SUGGESTIONS) {
                this.cleanupOnClose();
            }

            app.onGenericStringResult(action);
        } else {
            Log.d(this.TAG, "app not found");
        }
    }
};
