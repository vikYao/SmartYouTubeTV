package com.liskovsoft.smartyoutubetv.flavors.exoplayer.player.common.state;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection.Factory;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.player.ExoPlayerFragment;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.player.support.trackstate.PlayerStateManagerBase;
import com.liskovsoft.smartyoutubetv.flavors.exoplayer.player.support.trackstate.PlayerStateManagerBase.MyFormat;

public class TrackSelectionRestoreTrackSelector extends DefaultTrackSelector {
    private final Context mContext;
    private final PlayerStateManagerBase mStateManager;
    private boolean mAlreadyRestored;

    public TrackSelectionRestoreTrackSelector(Factory trackSelectionFactory, Context context) {
        super(trackSelectionFactory);
        mContext = context;
        mStateManager = new PlayerStateManagerBase(context);
    }

    // Ver 2.9.6
    //@Nullable
    //@Override
    //protected TrackSelection selectVideoTrack(TrackGroupArray groups, int[][] formatSupports, int mixedMimeTypeAdaptationSupports,
    //                                          Parameters params, @Nullable Factory adaptiveTrackSelectionFactory) throws ExoPlaybackException {
    //
    //    // Restore state before video starts playing
    //    boolean isAuto = !params.hasSelectionOverride(ExoPlayerFragment.RENDERER_INDEX_VIDEO, groups);
    //
    //    if (isAuto && !mAlreadyRestored) {
    //        mAlreadyRestored = true;
    //        restoreVideoTrack(groups);
    //    }
    //
    //    return super.selectVideoTrack(groups, formatSupports, mixedMimeTypeAdaptationSupports, params, adaptiveTrackSelectionFactory);
    //}

    // Ver 2.10.4
    @Nullable
    @Override
    protected TrackSelection.Definition selectVideoTrack(TrackGroupArray groups, int[][] formatSupports, int mixedMimeTypeAdaptationSupports,
                                              Parameters params, boolean enableAdaptiveTrackSelection) throws ExoPlaybackException {

        // Restore state before video starts playing
        boolean isAuto = !params.hasSelectionOverride(ExoPlayerFragment.RENDERER_INDEX_VIDEO, groups);

        if (isAuto && !mAlreadyRestored) {
            mAlreadyRestored = true;
            restoreVideoTrack(groups);
        }

        //unlockAllVideoFormats(formatSupports);

        return super.selectVideoTrack(groups, formatSupports, mixedMimeTypeAdaptationSupports, params, enableAdaptiveTrackSelection);
    }

    private void restoreVideoTrack(TrackGroupArray groups) {
        MyFormat format = mStateManager.findPreferredVideoFormat(groups);

        if (format != null) {
            setParameters(buildUponParameters().setSelectionOverride(
                    ExoPlayerFragment.RENDERER_INDEX_VIDEO,
                    groups,
                    new SelectionOverride(format.pair.first, format.pair.second)
            ));
        }
    }

    private void unlockAllVideoFormats(int[][] formatSupports) {
        final int videoTrackIndex = 0;

        for (int j = 0; j < formatSupports[videoTrackIndex].length; j++) {
            if (formatSupports[videoTrackIndex][j] == 19) { // video format not supported by system decoders
                formatSupports[videoTrackIndex][j] = 52; // force support of video format
            }
        }
    }
}
