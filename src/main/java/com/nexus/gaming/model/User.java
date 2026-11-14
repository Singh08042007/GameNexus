package com.nexus.gaming.model;

/**
 * Represents a registered NEXUS platform user.
 * Extends BaseEntity (Inheritance) and encapsulates user profile and statistics.
 */
public class User extends BaseEntity {

    private String email;
    private String username;
    private String passwordHash;
    private int wins;
    private int losses;
    private int draws;

    public User() {
        super();
    }

    public User(String id, String email, String username) {
        super(id, null, null);
        this.email = email;
        this.username = username;
        this.wins = 0;
        this.losses = 0;
        this.draws = 0;
    }

    public User(String id, String email, String username, int wins, int losses, int draws,
                String createdAt, String updatedAt) {
        super(id, createdAt, updatedAt);
        this.email = email;
        this.username = username;
        this.wins = wins;
        this.losses = losses;
        this.draws = draws;
    }

    @Override
    public String getEntityType() {
        return "USER";
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = Math.max(0, wins);
    }

    public int getLosses() {
        return losses;
    }

    public void setLosses(int losses) {
        this.losses = Math.max(0, losses);
    }

    public int getDraws() {
        return draws;
    }

    public void setDraws(int draws) {
        this.draws = Math.max(0, draws);
    }

    public synchronized void recordWin() {
        this.wins++;
        touch();
    }

    public synchronized void recordLoss() {
        this.losses++;
        touch();
    }

    public synchronized void recordDraw() {
        this.draws++;
        touch();
    }

    public int getTotalMatches() {
        return wins + losses + draws;
    }
}
