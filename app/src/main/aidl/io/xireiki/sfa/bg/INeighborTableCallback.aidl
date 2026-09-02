package io.xireiki.sfa.bg;

import io.xireiki.sfa.bg.ParceledListSlice;

interface INeighborTableCallback {
    oneway void onNeighborTableUpdated(in ParceledListSlice entries);
}
