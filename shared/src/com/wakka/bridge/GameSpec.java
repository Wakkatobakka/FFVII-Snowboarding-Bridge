package com.wakka.bridge;

/** Title identity and facts. Native key semantics belong to its backend/profile. */
public final class GameSpec {
    public final String id, title, subtitle, baseline, family, playLabel;
    public final String logoAsset, backdropAsset, controlsHelp, knownIssues;
    public final int accent;
    public GameSpec(String id, String title, String subtitle, String baseline,
                    String family, String playLabel, String logoAsset,
                    String backdropAsset, String controlsHelp, String knownIssues, int accent) {
        this.id=id; this.title=title; this.subtitle=subtitle; this.baseline=baseline;
        this.family=family; this.playLabel=playLabel; this.logoAsset=logoAsset;
        this.backdropAsset=backdropAsset; this.controlsHelp=controlsHelp;
        this.knownIssues=knownIssues; this.accent=accent;
    }
}
