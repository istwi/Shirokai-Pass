package br.com.shirokai.pass;

import java.util.*;

public final class Model {
  private Model() {}

  public static class Config {
    public int schema = 1;
    public String premiumInfo = "Consulte a equipe para adquirir Premium.";
    public String rules = "Ganhe XP em atividades do servidor e resgate suas recompensas.";
    public boolean announcements = true;
    public String levelAnnouncement = "✦ {player} alcançou o nível {level} no {season}!";
    public Map<String, String> icons =
        new HashMap<>(
            Map.of(
                "rewards",
                "minecraft:chest",
                "profile",
                "minecraft:player_head",
                "premium",
                "minecraft:gold_ingot",
                "history",
                "minecraft:book",
                "claim",
                "minecraft:lime_dye",
                "info",
                "minecraft:paper",
                "pending",
                "minecraft:hopper"));
    public long baseXp = 100, incrementXp = 25;

    public void validate() {
      if (schema != 1
          || baseXp <= 0
          || incrementXp < 0
          || icons == null
          || premiumInfo == null
          || rules == null
          || levelAnnouncement == null) throw new IllegalArgumentException("Configuração inválida");
      threshold(100, baseXp, incrementXp);
    }
  }

  public static class State {
    public int schema = 1;
    public String active;
    public Map<String, Season> seasons = new LinkedHashMap<>();
    public Map<String, Progress> players = new HashMap<>();
    public Map<String, UUID> names = new HashMap<>();
    public Map<String, Receipt> transactions = new HashMap<>();
    public Map<String, Delivery> deliveries = new LinkedHashMap<>();
    public List<XpEvent> events = new ArrayList<>();
    public Map<String, Long> premiumUntil = new HashMap<>();
    public Map<String, PremiumReceipt> premiumTransactions = new HashMap<>();
  }

  public static class Season {
    public String id, name;
    public long start, end, graceEnd, baseXp = 100, incrementXp = 25;
    public Map<String, Reward> rewards = new LinkedHashMap<>();

    public Season() {}

    public Season(String id, long now, long duration, long grace, long base, long increment) {
      if (!id.matches("[a-zA-Z0-9_-]{1,64}") || duration <= 0 || grace < 0)
        throw new IllegalArgumentException("Temporada inválida");
      this.id = id;
      name = id;
      start = now;
      end = Math.addExact(now, duration);
      graceEnd = Math.addExact(end, grace);
      baseXp = base;
      incrementXp = increment;
      threshold(100, base, increment);
      for (int level = 1; level <= 100; level++)
        for (boolean premium : new boolean[] {false, true}) {
          Reward r = new Reward();
          r.enabled = false;
          r.level = level;
          r.premium = premium;
          r.choices.add(new Choice("item", "minecraft:diamond", premium ? 2 : 1, 1));
          rewards.put(key(level, premium), r);
        }
    }

    public long cap() {
      return threshold(100, baseXp, incrementXp);
    }

    public boolean earning(long now) {
      return now >= start && now < end;
    }

    public boolean claiming(long now) {
      return now >= start && now < graceEnd;
    }
  }

  public static class Progress {
    public long xp;
    public boolean premium;
    public Set<String> claimed = new HashSet<>();
  }

  public static class Reward {
    public int level;
    public boolean premium;
    public boolean enabled = true;
    public String mode = "fixed";
    public List<Choice> choices = new ArrayList<>();

    public void validate() {
      if (level < 1
          || level > 100
          || choices == null
          || choices.isEmpty()
          || choices.size() > 100
          || !(mode.equals("fixed") || mode.equals("random")))
        throw new IllegalArgumentException("Recompensa inválida");
      long sum = 0;
      for (Choice c : choices) {
        c.validate();
        sum = Math.addExact(sum, c.weight);
      }
      if (sum <= 0) throw new IllegalArgumentException("Pesos inválidos");
    }
  }

  public static class Choice {
    public String type = "item", value = "minecraft:diamond";
    public int count = 1;
    public long weight = 1;

    public Choice() {}

    public Choice(String type, String value, int count, long weight) {
      this.type = type;
      this.value = value;
      this.count = count;
      this.weight = weight;
    }

    public void validate() {
      if (!Set.of("item", "command", "pokemon").contains(type)
          || value == null
          || value.isBlank()
          || value.contains("\n")
          || value.contains("\r")
          || count < 1
          || count > 2304
          || (!type.equals("item") && count != 1)
          || value.length() > 4096
          || weight <= 0) throw new IllegalArgumentException("Prêmio inválido");
    }
  }

  public static class XpEvent {
    public double multiplier;
    public long start, end;
  }

  public static class Receipt {
    public String player, season;
    public long amount, applied;
    public boolean bypass;
  }

  public static class PremiumReceipt {
    public String player;
    public long duration, expires;
  }

  public static String premiumDate(long timestamp) {
    return java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(java.time.ZoneId.of("America/Sao_Paulo"))
            .format(java.time.Instant.ofEpochMilli(timestamp))
        + " (Brasília)";
  }

  public static class Delivery {
    public String id, season, player, reward, status = "READY", error = "";
    public List<Choice> prizes = new ArrayList<>();
  }

  public record XpResult(
      long before,
      long after,
      long original,
      double multiplier,
      boolean duplicate,
      int levelBefore,
      int levelAfter) {}

  public static String key(int level, boolean premium) {
    return (premium ? "premium:" : "free:") + level;
  }

  public static String playerKey(String season, UUID player) {
    return season + "/" + player;
  }

  public static long threshold(int level, long base, long increment) {
    if (level < 0 || level > 100 || base <= 0 || increment < 0)
      throw new IllegalArgumentException("Curva inválida");
    return Math.addExact(
        Math.multiplyExact(level, base),
        Math.multiplyExact((long) level * (level - 1) / 2, increment));
  }

  public static int level(Season season, long xp) {
    int n = 0;
    while (n < 100 && xp >= threshold(n + 1, season.baseXp, season.incrementXp)) n++;
    return n;
  }
}
