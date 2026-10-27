package com.bytedance.chemistrylab

import com.bytedance.chemistrylab.content.LabVolume
import com.bytedance.chemistrylab.lab.DemoRegistry
import com.bytedance.chemistrylab.lab.LabHost
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        LabHost.register(DemoRegistry.all)
        DefaultWindowContainer {
            PicoTheme {
                LabVolume(firstDemo = DemoRegistry.FIRST)
            }
        }
    }
