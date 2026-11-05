package net.minecraft.nbt;

import java.util.ArrayList;
import java.util.List;

/** NBT de mentira para PruebaGuardado (sin Minecraft): solo lo que usa GuardadoPolitica. */
public class ListTag implements Tag {
	private final List<Tag> l = new ArrayList<>();

	public boolean add(Tag t) {
		return l.add(t);
	}

	public int size() {
		return l.size();
	}

	public CompoundTag getCompoundOrEmpty(int i) {
		return i < l.size() && l.get(i) instanceof CompoundTag c ? c : new CompoundTag();
	}

	@Override
	public int bytes() {
		int n = 5;
		for (Tag t : l) {
			n += t.bytes();
		}
		return n;
	}

	@Override
	public String toString() {
		return l.toString();
	}
}
