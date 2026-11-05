package net.minecraft.nbt;

/** NBT de mentira para PruebaGuardado (sin Minecraft): solo lo que usa GuardadoPolitica. */
public interface Tag {
	/** Lo que ocuparía en disco sin comprimir, más o menos como el NBT de verdad. */
	int bytes();
}
