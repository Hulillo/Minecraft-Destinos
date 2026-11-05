package net.minecraft.nbt;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** NBT de mentira para PruebaGuardado (sin Minecraft): solo lo que usa GuardadoPolitica. */
public class CompoundTag implements Tag {
	private final Map<String, Object> m = new LinkedHashMap<>();

	public void putInt(String k, int v) {
		m.put(k, v);
	}

	public void putLong(String k, long v) {
		m.put(k, v);
	}

	public void putString(String k, String v) {
		m.put(k, v);
	}

	public void putBoolean(String k, boolean v) {
		m.put(k, (byte) (v ? 1 : 0));
	}

	public void put(String k, Tag t) {
		m.put(k, t);
	}

	public boolean contains(String k) {
		return m.containsKey(k);
	}

	public int getIntOr(String k, int d) {
		return m.get(k) instanceof Integer v ? v : d;
	}

	public long getLongOr(String k, long d) {
		return m.get(k) instanceof Long v ? v : d;
	}

	public String getStringOr(String k, String d) {
		return m.get(k) instanceof String v ? v : d;
	}

	public Optional<String> getString(String k) {
		return m.get(k) instanceof String v ? Optional.of(v) : Optional.empty();
	}

	public boolean getBooleanOr(String k, boolean d) {
		return m.get(k) instanceof Byte v ? v != 0 : d;
	}

	public ListTag getListOrEmpty(String k) {
		return m.get(k) instanceof ListTag v ? v : new ListTag();
	}

	public CompoundTag getCompoundOrEmpty(String k) {
		return m.get(k) instanceof CompoundTag v ? v : new CompoundTag();
	}

	@Override
	public int bytes() {
		int n = 1;
		for (Map.Entry<String, Object> e : m.entrySet()) {
			n += 3 + e.getKey().length();
			Object o = e.getValue();
			n += o instanceof Integer ? 4 : o instanceof Long ? 8 : o instanceof Byte ? 1
					: o instanceof String s ? 2 + s.getBytes(StandardCharsets.UTF_8).length : ((Tag) o).bytes();
		}
		return n;
	}

	@Override
	public String toString() {
		return m.toString();
	}
}
