package com.jobtracker.migrationpoc.database;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ScheduleAdvicePlanner {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FORMAT_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long IDEAL_MINUTES = 90;
    private static final long MINIMUM_MINUTES = 60;
    private final ObjectMapper mapper;

    ScheduleAdvicePlanner(ObjectMapper mapper) { this.mapper = mapper; }

    ObjectNode plan(JsonNode input, LocalDateTime now) {
        List<Item> fixed = new ArrayList<>(), flexible = new ArrayList<>();
        for (JsonNode node : input) {
            LocalDateTime start = parse(node.path("startsAt").asText(""));
            LocalDateTime end = parse(node.path("endsAt").asText(""));
            if (start == null) continue;
            Item item = new Item(node.path("id").asText(""), label(node), start, end);
            if (end != null && end.isAfter(start)) flexible.add(item); else fixed.add(item);
        }
        fixed.sort(Comparator.comparing(Item::start));
        flexible.sort(Comparator.comparing(item -> item.end));
        List<Slot> occupied = new ArrayList<>(), plans = new ArrayList<>();
        List<Item> failedFlexible = new ArrayList<>();
        Set<Item> tightItems = new HashSet<>(), conflictItems = new HashSet<>();
        List<String> warnings = new ArrayList<>(), conflicts = new ArrayList<>();

        for (int index = 0; index < fixed.size(); index++) {
            Item item = fixed.get(index);
            if (item.start.isBefore(now)) continue;
            LocalDateTime idealEnd = item.start.plusMinutes(IDEAL_MINUTES), next = null;
            for (int other = index + 1; other < fixed.size(); other++) {
                if (fixed.get(other).start.isAfter(item.start)) { next = fixed.get(other).start; break; }
            }
            long available = next == null ? IDEAL_MINUTES : Math.min(IDEAL_MINUTES, Duration.between(item.start, next).toMinutes());
            boolean sameStart = fixed.stream().filter(candidate -> candidate.start.equals(item.start)).count() > 1;
            if (sameStart) {
                Slot slot = new Slot(item, item.start, idealEnd); plans.add(slot); occupied.add(slot); conflictItems.add(item);
            } else if (available >= MINIMUM_MINUTES) {
                Slot slot = new Slot(item, item.start, item.start.plusMinutes(available)); plans.add(slot); occupied.add(slot);
                if (available < IDEAL_MINUTES) { tightItems.add(item); warnings.add(item.label + "：仅安排 " + available + " 分钟，时间紧张"); }
            } else {
                Slot slot = new Slot(item, item.start, idealEnd); plans.add(slot); occupied.add(slot); conflictItems.add(item);
                conflicts.add(item.label + "：距下一项固定日程不足 60 分钟");
            }
        }
        for (int left = 0; left < fixed.size(); left++) for (int right = left + 1; right < fixed.size(); right++) {
            if (fixed.get(left).start.equals(fixed.get(right).start) && !fixed.get(left).start.isBefore(now)) {
                conflicts.add(fixed.get(left).label + " 与 " + fixed.get(right).label + " 的固定开始时间冲突");
            }
        }

        occupied.sort(Comparator.comparing(Slot::start));
        for (Item item : flexible) {
            LocalDateTime windowStart = item.start.isAfter(now) ? item.start : now;
            if (!item.end.isAfter(windowStart.plusMinutes(MINIMUM_MINUTES - 1))) {
                conflictItems.add(item); failedFlexible.add(item); conflicts.add(item.label + "：剩余可用时间不足 60 分钟"); continue;
            }
            Slot slot = findSlot(item, windowStart, item.end, IDEAL_MINUTES, occupied);
            if (slot == null) slot = findBestShortSlot(item, windowStart, item.end, occupied);
            if (slot == null) {
                conflictItems.add(item); failedFlexible.add(item); conflicts.add(item.label + "：可用时间段内无法安排连续 60 分钟"); continue;
            }
            plans.add(slot); occupied.add(slot); occupied.sort(Comparator.comparing(Slot::start));
            long minutes = Duration.between(slot.start, slot.end).toMinutes();
            if (minutes < IDEAL_MINUTES) { tightItems.add(item); warnings.add(item.label + "：仅安排 " + minutes + " 分钟，时间紧张"); }
        }
        plans.sort(Comparator.comparing(Slot::start).thenComparing(slot -> slot.item.label));
        ObjectNode result = mapper.createObjectNode();
        result.put("summary", "已安排 " + plans.size() + " 项日程" + (warnings.isEmpty() ? "" : "，其中 " + warnings.size() + " 项时间紧张") + (conflicts.isEmpty() ? "" : "，发现 " + conflicts.stream().distinct().count() + " 处冲突"));
        ArrayNode planArray = result.putArray("plans");
        plans.forEach(slot -> planArray.add(FORMAT.format(slot.start) + "-" + slot.end.format(DateTimeFormatter.ofPattern("HH:mm")) + " " + slot.item.label));
        ArrayNode timeline = result.putArray("timeline");
        for (Slot slot : plans) addTimeline(timeline, slot.item, slot.start, slot.end, status(slot.item, tightItems, conflictItems), freelyAdjustable(slot.item, plans));
        for (Item item : failedFlexible) addTimeline(timeline, item, item.start.isAfter(now) ? item.start : now, item.end, "conflict", true);
        ArrayNode warningArray = result.putArray("warnings"); warnings.forEach(warningArray::add);
        ArrayNode conflictArray = result.putArray("conflicts"); conflicts.stream().distinct().forEach(conflictArray::add);
        return result;
    }

    private void addTimeline(ArrayNode output, Item item, LocalDateTime start, LocalDateTime end, String status, boolean showWindow) {
        ObjectNode node = output.addObject();
        node.put("id", item.id); node.put("label", item.label); node.put("date", start.toLocalDate().toString());
        node.put("start", start.format(DateTimeFormatter.ofPattern("HH:mm"))); node.put("end", end.format(DateTimeFormatter.ofPattern("HH:mm")));
        node.put("status", status); node.put("flexible", item.end != null && item.end.isAfter(item.start)); node.put("showWindow", showWindow);
        if (item.end != null && item.end.isAfter(item.start)) {
            node.put("windowStart", item.start.format(FORMAT)); node.put("windowEnd", item.end.format(FORMAT));
        }
    }

    private String status(Item item, Set<Item> tight, Set<Item> conflicts) { return conflicts.contains(item) ? "conflict" : tight.contains(item) ? "tight" : "normal"; }
    private boolean freelyAdjustable(Item item, List<Slot> plans) {
        if (item.end == null || !item.end.isAfter(item.start)) return false;
        return plans.stream().filter(slot -> !slot.item.equals(item)).noneMatch(slot -> slot.start.isBefore(item.end) && item.start.isBefore(slot.end));
    }

    private Slot findSlot(Item item, LocalDateTime start, LocalDateTime end, long minutes, List<Slot> occupied) {
        LocalDateTime candidate = start;
        for (Slot busy : occupied) {
            if (!busy.end.isAfter(candidate)) continue;
            if (!busy.start.isBefore(candidate.plusMinutes(minutes)) && !candidate.plusMinutes(minutes).isAfter(end)) return new Slot(item, candidate, candidate.plusMinutes(minutes));
            if (busy.start.isBefore(candidate.plusMinutes(minutes))) candidate = busy.end.isAfter(candidate) ? busy.end : candidate;
        }
        return candidate.plusMinutes(minutes).isAfter(end) ? null : new Slot(item, candidate, candidate.plusMinutes(minutes));
    }

    private Slot findBestShortSlot(Item item, LocalDateTime start, LocalDateTime end, List<Slot> occupied) {
        LocalDateTime cursor = start; Slot best = null;
        for (Slot busy : occupied) {
            if (!busy.end.isAfter(cursor)) continue;
            LocalDateTime gapEnd = busy.start.isBefore(end) ? busy.start : end;
            long gap = Duration.between(cursor, gapEnd).toMinutes();
            if (gap >= MINIMUM_MINUTES && (best == null || gap > Duration.between(best.start, best.end).toMinutes())) best = new Slot(item, cursor, cursor.plusMinutes(Math.min(IDEAL_MINUTES, gap)));
            if (busy.end.isAfter(cursor)) cursor = busy.end;
            if (!cursor.isBefore(end)) break;
        }
        long tail = Duration.between(cursor, end).toMinutes();
        if (tail >= MINIMUM_MINUTES && (best == null || tail > Duration.between(best.start, best.end).toMinutes())) best = new Slot(item, cursor, cursor.plusMinutes(Math.min(IDEAL_MINUTES, tail)));
        return best;
    }

    private String label(JsonNode node) { return node.path("company").asText("未填写公司") + " · " + node.path("title").asText("未命名日程"); }
    private LocalDateTime parse(String value) {
        String normalized = value == null ? "" : value.trim().replace('T', ' ');
        for (DateTimeFormatter formatter : List.of(FORMAT, FORMAT_SECONDS)) {
            try { return LocalDateTime.parse(normalized, formatter); } catch (DateTimeParseException ignored) { }
        }
        return null;
    }
    private record Item(String id, String label, LocalDateTime start, LocalDateTime end) {}
    private record Slot(Item item, LocalDateTime start, LocalDateTime end) {}
}