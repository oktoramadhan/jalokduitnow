package com.okto.jalokduitnow.achievement;

/**
 * A badge. Abstraction: each subclass defines its own unlock rule in {@link #check}; the game service just asks.
 * The icon is a key into the app's icon catalog (no emoji). Once unlocked, a badge stays unlocked
 * (the service stores the unlock date), so a rule that stops being true later does not take it away.
 */
public abstract class Achievement {
    private final String id;
    private final String name;
    private final String description;
    private final String iconKey;

    protected Achievement(String id, String name, String description, String iconKey) {
        assert id != null && !id.isBlank() && name != null && description != null && iconKey != null;
        this.id = id;
        this.name = name;
        this.description = description;
        this.iconKey = iconKey;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    /** Also the hint shown while the badge is locked. */
    public String getDescription() { return description; }
    public String getIconKey() { return iconKey; }

    public abstract boolean check(GameContext context);

    /** Extra XP on top of the usual badge XP when this badge unlocks (0 for most badges). */
    public int bonusXp() {
        return 0;
    }
}
