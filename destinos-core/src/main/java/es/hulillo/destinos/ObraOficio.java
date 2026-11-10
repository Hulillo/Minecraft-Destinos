package es.hulillo.destinos;

import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * v0.6.3 La obra del oficio de un pueblo propio, mirando a su plaza: la torre de vigía con empalizada del puesto, la
 * bocamina con castillete, pozo, galería y fundición de la mina, la serrería con sus pilas de troncos y la carbonera
 * del aserradero, la lonja del puerto, el molino de viento con su granero de la aldea y la taberna de la ciudad.
 * Cada una con los puestos de trabajo de su gente dentro (alto horno, cortapiedras, barriles, compostador…).
 */
public final class ObraOficio extends PiezaObra {
	public static final StructurePieceType TIPO = (StructurePieceType.ContextlessType) ObraOficio::new;
	/** Medio lado de la obra en planta (como una casa noble). */
	static final int FONDO = 9;
	private static final int ALTO = 24;

	private final MapaTerreno.TipoPueblo oficio;

	ObraOficio(MapaTerreno.TipoPueblo oficio, int cx, int cz, int y0, int rot, int estilo, int madera, int color) {
		super(TIPO, cx, cz, y0, rot, estilo, madera, color, FONDO + TALUD, ALTO);
		this.oficio = oficio;
	}

	private ObraOficio(CompoundTag t) {
		super(TIPO, t);
		int o = t.getIntOr("oficio", 0);
		MapaTerreno.TipoPueblo[] tipos = MapaTerreno.TipoPueblo.values();
		oficio = tipos[o >= 0 && o < tipos.length ? o : 0];
	}

	static void registra() {
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "obra_oficio"), TIPO);
	}

	@Override
	protected void addAdditionalSaveData(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext ctx, CompoundTag t) {
		super.addAdditionalSaveData(ctx, t);
		t.putInt("oficio", oficio.ordinal());
	}

	/** El botín de cada oficio (data/destinos_core/loot_table/chests/oficio/<oficio>.json): flechas y armas en el
	 *  puesto, mineral en la mina, troncos y carbón en el aserradero, pescado en el puerto, grano en la aldea, comida y
	 *  bebida en la taberna de la ciudad. */
	private static final ResourceKey<LootTable>[] BOTIN = botines();

	@SuppressWarnings("unchecked")
	private static ResourceKey<LootTable>[] botines() {
		MapaTerreno.TipoPueblo[] tipos = MapaTerreno.TipoPueblo.values();
		ResourceKey<LootTable>[] b = new ResourceKey[tipos.length];
		for (MapaTerreno.TipoPueblo t : tipos) {
			b[t.ordinal()] = ResourceKey.create(Registries.LOOT_TABLE,
					Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "chests/oficio/" + t.name()));
		}
		return b;
	}

	@Override
	protected ResourceKey<LootTable> botinPropio() {
		return BOTIN[oficio.ordinal()];
	}

	@Override
	protected void construye(Obra o) {
		switch (oficio) {
			case puesto -> torre(o);
			case mina -> mina(o);
			case aserradero -> serreria(o);
			case puerto -> lonja(o);
			case aldea -> molino(o);
			case ciudad -> taberna(o);
		}
		o.alumbra(-FONDO, -FONDO, FONDO, FONDO, Obra.MIN, ALTO - 4, 5);
	}

	/** La muela de piedra (una piedra de afilar en el suelo). */
	private static BlockState muela(Direction mira) {
		return Obra.mira(Blocks.GRINDSTONE, mira).setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
	}

	// ---- el puesto: torre de vigía, empalizada y barracón ----

	private static final int TORRE = 3;
	private static final int PISO_TORRE = 5;

	private void torre(Obra o) {
		Paleta p = o.p;
		int r = FONDO - 1;
		o.planta(-r, -r, r, r);
		// la empalizada: troncos de punta alrededor, con el portón hacia la plaza
		BlockState estaca = Obra.tronco(p.tronco, Direction.Axis.Y);
		for (int u = -r; u <= r; u++) {
			for (int v = -r; v <= r; v++) {
				if (Math.abs(u) != r && Math.abs(v) != r || v == -r && Math.abs(u) <= 1) {
					continue;
				}
				int alto = 2 + o.azar(u, 0, v, 2);
				o.caja(u, 1, v, u, alto, v, estaca);
				o.pon(u, alto + 1, v, p.valla);
			}
		}
		o.caja(-2, 1, -r, -2, 4, -r, estaca);
		o.caja(2, 1, -r, 2, 4, -r, estaca);
		o.viga(-2, -r, 2, -r, 5);
		o.estandarte(0, 4, -r, Direction.NORTH);
		// la torre: cuatro pisos de piedra, troneras, escala por dentro y almenas arriba
		int t = TORRE;
		int arriba = 3 * PISO_TORRE;
		o.sillares(-t, 0, -t, t, arriba, t, true);
		o.caja(-t + 1, 0, -t + 1, t - 1, 0, t - 1, p.zocalo);
		o.aire(-t + 1, 1, -t + 1, t - 1, arriba - 1, t - 1);
		for (int u : new int[] {-t, t}) {
			for (int v : new int[] {-t, t}) {
				o.caja(u, 0, v, u, arriba, v, p.moldura);
			}
		}
		for (int piso = 1; piso <= 3; piso++) {
			o.caja(-t + 1, piso * PISO_TORRE, -t + 1, t - 1, piso * PISO_TORRE, t - 1, p.tablas);
		}
		o.puerta(0, 1, -t, Direction.NORTH);
		o.pon(0, 3, -t, p.moldura);
		// troneras en los dos pisos de arriba, en las cuatro caras
		for (int piso = 1; piso <= 2; piso++) {
			int y = piso * PISO_TORRE + 2;
			o.aire(0, y, -t, 0, y + 1, -t);
			o.aire(0, y, t, 0, y + 1, t);
			o.aire(-t, y, 0, -t, y + 1, 0);
			o.aire(t, y, 0, t, y + 1, 0);
		}
		// la escala, pegada a la pared de atrás, de abajo a la azotea
		o.caja(t - 1, 1, t - 1, t - 1, arriba, t - 1, Obra.mira(Blocks.LADDER, Direction.NORTH));
		// abajo: la guardia y el armero
		o.ser(SeresObra.GUARDIA, -t + 1, 1, t - 1, Direction.NORTH);
		o.toneles(-t + 1, 1, -t + 1, -t + 1, 1, -t + 2);
		o.pon(t - 1, 1, -t + 1, Obra.mira(Blocks.CHEST, Direction.WEST));
		o.pon(0, PISO_TORRE - 1, 0, Obra.farolColgado());
		// el primer piso: dos catres con la cabecera en la pared del oeste y la mesilla en medio
		int y1 = PISO_TORRE + 1;
		o.cama(-t + 1, y1, -1, Direction.WEST, Blocks.BROWN_BED);
		o.cama(-t + 1, y1, 1, Direction.WEST, Blocks.BROWN_BED);
		o.tonel(-t + 1, y1, 0);
		o.pon(-t + 1, y1 + 1, 0, Obra.velas(2));
		// el segundo: los arqueros, con su diana y el cofre de flechas
		int y2 = 2 * PISO_TORRE + 1;
		o.pon(-t + 1, y2, -t + 1, Blocks.TARGET);
		o.pon(-t + 1, y2, t - 1, Obra.mira(Blocks.CHEST, Direction.EAST));
		o.pon(1, y2, -t + 1, Blocks.FLETCHING_TABLE);
		// la azotea: almenas, la hoguera para dar aviso y el estandarte del reino
		int ya = arriba + 1;
		for (int u = -t; u <= t; u++) {
			for (int v = -t; v <= t; v++) {
				if ((Math.abs(u) == t || Math.abs(v) == t) && Math.floorMod(u + v, 2) == 0) {
					o.pon(u, ya, v, p.muroBajo);
				}
			}
		}
		o.pon(-1, ya, -1, Blocks.CAMPFIRE);
		o.pon(1, ya, 1, Blocks.HAY_BLOCK);
		o.aguja(-1, ya, 1);
		// el barracón, detrás de la torre y pegado a la empalizada
		barracon(o, -r + 1, t + 2, -1, r - 1);
	}

	/**
	 * Un barracón bajo de madera de ua a ub (u) y de va a vb (v, tres de fondo), con la puerta en medio de la pared de
	 * delante y un catre a cada lado.
	 */
	private static void barracon(Obra o, int ua, int va, int ub, int vb) {
		Paleta p = o.p;
		int um = (ua + ub) / 2;
		int vm = (va + vb) / 2;
		o.caja(ua, 0, va, ub, 0, vb, p.tablas);
		o.paredes(ua, 1, va, ub, 3, vb, p.tablas);
		for (int u : new int[] {ua, ub}) {
			for (int v : new int[] {va, vb}) {
				o.caja(u, 1, v, u, 3, v, Obra.tronco(p.tronco, Direction.Axis.Y));
			}
		}
		o.aire(ua + 1, 1, va + 1, ub - 1, 3, vb - 1);
		o.puerta(um, 1, va, Direction.NORTH);
		o.pon(um, 2, vb, Blocks.GLASS_PANE);
		o.cama(ua + 1, 1, vm, Direction.WEST, Blocks.BROWN_BED);
		o.cama(ub - 1, 1, vm, Direction.EAST, Blocks.BROWN_BED);
		o.pon(um, 3, vm, Obra.farolColgado());
		o.dosAguas(ua - 1, va - 1, ub + 1, vb + 1, 4, false, p.tablas);
	}

	// ---- la mina: castillete sobre el pozo, galería con vetas, tolva y la fundición ----

	/** Donde baja el pozo (u, v) y lo hondo que llega la galería. */
	private static final int POZO_U = 4;
	private static final int POZO_V = 0;
	private static final int HONDO = -9;

	private void mina(Obra o) {
		Paleta p = o.p;
		o.planta(-FONDO + 1, -FONDO + 1, FONDO - 1, FONDO - 1);
		fundicion(o, -8, -5, -1, 5);
		// el pozo: brocal de piedra, la escala y el hueco hasta la galería
		int pu = POZO_U;
		int pv = POZO_V;
		o.caja(pu - 1, 0, pv - 1, pu + 1, 0, pv + 1, p.zocalo);
		o.caja(pu - 1, HONDO, pv - 1, pu + 1, -1, pv + 1, Blocks.STONE);
		o.aire(pu, HONDO + 1, pv, pu, 0, pv);
		o.caja(pu, HONDO + 1, pv, pu, 0, pv, Obra.mira(Blocks.LADDER, Direction.NORTH));
		o.pon(pu - 1, 1, pv - 1, p.muroBajo);
		o.pon(pu + 1, 1, pv - 1, p.muroBajo);
		o.pon(pu - 1, 1, pv + 1, p.muroBajo);
		o.pon(pu + 1, 1, pv + 1, p.muroBajo);
		// el castillete: cuatro patas de tronco, el cabezal con la polea y la cadena hasta el brocal
		for (int du : new int[] {-2, 2}) {
			for (int dv : new int[] {-2, 2}) {
				o.caja(pu + du, 1, pv + dv, pu + du, 7, pv + dv, Obra.tronco(p.tronco, Direction.Axis.Y));
			}
			o.viga(pu + du, pv - 2, pu + du, pv + 2, 8);
		}
		o.viga(pu - 2, pv, pu + 2, pv, 9);
		o.pon(pu, 10, pv, muela(Direction.EAST));
		o.caja(pu, 2, pv, pu, 8, pv, p.valla);
		o.caja(pu - 1, 8, pv - 2, pu + 1, 8, pv - 2, Obra.escalon(p.maderaEscalon, Direction.SOUTH, true));
		o.caja(pu - 1, 8, pv + 2, pu + 1, 8, pv + 2, Obra.escalon(p.maderaEscalon, Direction.NORTH, true));
		// la galería, de la escala hacia atrás, con entibos, raíles y vetas en las paredes
		galeria(o, pu, pv);
		// la tolva sobre el cofre, junto al brocal, y la vagoneta de raíles hasta la fundición
		o.pon(pu + 3, 1, pv - 3, Obra.mira(Blocks.CHEST, Direction.NORTH));
		o.pon(pu + 3, 2, pv - 3, Blocks.HOPPER);
		o.caja(pu + 3, 3, pv - 3, pu + 3, 3, pv - 3, Obra.losa(p.maderaLosa, false));
		BlockState rail = Blocks.RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE, RailShape.EAST_WEST);
		for (int u = 0; u <= pu - 2; u++) {
			o.pon(u, 0, pv - 3, Blocks.GRAVEL);
			o.pon(u, 1, pv - 3, rail);
		}
		// la escombrera: un montón de zahorra, grava y mineral pobre detrás del castillete
		for (int du = -2; du <= 2; du++) {
			for (int dv = -2; dv <= 2; dv++) {
				int h = 3 - Math.max(Math.abs(du), Math.abs(dv)) - o.azar(du, 0, dv, 2);
				for (int y = 1; y <= h; y++) {
					int a = o.azar(du, y, dv, 6);
					o.pon(pu + 1 + du, y, 6 + dv, a < 3 ? Blocks.COBBLESTONE : a < 5 ? Blocks.GRAVEL : Blocks.COAL_ORE);
				}
			}
		}
		o.alumbra(pu - 1, pv, pu + 1, FONDO - 1, HONDO, -6, 4);
	}

	private void galeria(Obra o, int pu, int pv) {
		Paleta p = o.p;
		int suelo = HONDO;
		int vFin = FONDO - 1;
		Block[] vetas = {Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.STONE, Blocks.STONE, Blocks.ANDESITE, Blocks.TUFF};
		for (int v = pv; v <= vFin; v++) {
			for (int u = pu - 2; u <= pu + 2; u++) {
				for (int y = suelo; y <= suelo + 4; y++) {
					boolean pared = Math.abs(u - pu) == 2 || y == suelo || y == suelo + 4 || v == vFin;
					if (pared) {
						Block b = vetas[o.azar(u, y, v, vetas.length)];
						if (y == suelo && Math.abs(u - pu) < 2) {
							b = Blocks.STONE;
						}
						o.pon(u, y, v, b);
					} else if (!(u == pu && v == pv)) {
						o.pon(u, y, v, Blocks.AIR);
					}
				}
			}
			// entibo cada tres: dos postes de valla y un larguero de tronco
			if ((v - pv) % 3 == 2) {
				o.caja(pu - 1, suelo + 1, v, pu - 1, suelo + 2, v, p.valla);
				o.caja(pu + 1, suelo + 1, v, pu + 1, suelo + 2, v, p.valla);
				o.viga(pu - 1, v, pu + 1, v, suelo + 3);
			}
			if (v > pv) {
				o.pon(pu, suelo + 1, v, Blocks.RAIL);
			}
		}
		// el frente de la galería: la veta buena y el cofre de los mineros
		o.pon(pu, suelo + 2, vFin, Blocks.IRON_ORE);
		o.pon(pu - 1, suelo + 1, vFin - 1, Obra.mira(Blocks.CHEST, Direction.EAST));
		o.pon(pu + 1, suelo + 1, vFin - 1, Blocks.CRAFTING_TABLE);
	}

	/** La fundición: nave de piedra con dos altos hornos, yunque, banco de herrero y el carbón. */
	private static void fundicion(Obra o, int ua, int va, int ub, int vb) {
		Paleta p = o.p;
		int techo = 5;
		o.caja(ua, 0, va, ub, 0, vb, p.zocalo);
		o.sillares(ua, 1, va, ub, techo - 1, vb, true);
		o.aire(ua + 1, 1, va + 1, ub - 1, techo - 1, vb - 1);
		o.caja(ua + 1, techo, va + 1, ub - 1, techo, vb - 1, p.tablas);
		int um = (ua + ub) / 2;
		o.puertaDoble(um, 1, va, Direction.EAST, Direction.NORTH);
		o.pon(ua, 2, 0, Blocks.GLASS_PANE);
		o.pon(ub, 2, vb - 2, Blocks.GLASS_PANE);
		// los hornos contra la pared de atrás, con la chimenea de ladrillo que sale por el tejado
		o.pon(ua + 2, 1, vb - 1, Obra.mira(Blocks.BLAST_FURNACE, Direction.NORTH));
		o.pon(ua + 4, 1, vb - 1, Obra.mira(Blocks.BLAST_FURNACE, Direction.NORTH));
		o.pon(ua + 3, 1, vb - 1, Blocks.COAL_BLOCK);
		o.caja(ua + 2, 2, vb - 1, ua + 4, techo - 1, vb - 1, p.ladrillo);
		o.caja(ua + 3, techo, vb - 1, ua + 3, techo + 6, vb - 1, p.ladrillo);
		o.pon(ua + 3, techo + 7, vb - 1, Blocks.CAMPFIRE);
		o.pon(ua + 1, 1, 0, Blocks.ANVIL);
		o.pon(ua + 1, 1, -2, Blocks.SMITHING_TABLE);
		o.pon(ub - 1, 1, vb - 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		o.pon(ub - 1, 1, va + 1, Obra.mira(Blocks.CHEST, Direction.WEST));
		o.pon(ub - 1, 1, va + 2, Blocks.RAW_IRON_BLOCK);
		o.pon(ub - 1, 1, va + 3, Blocks.RAW_COPPER_BLOCK);
		o.pon(ua + 1, 1, vb - 1, Blocks.COAL_BLOCK);
		o.pon(ua + 1, 2, vb - 1, Blocks.COAL_BLOCK);
		o.dosAguas(ua - 1, va - 1, ub + 1, vb + 1, techo, true, p.tablas);
	}

	// ---- el aserradero: serrería, pilas de troncos y carbonera ----

	private void serreria(Obra o) {
		Paleta p = o.p;
		o.planta(-FONDO + 1, -FONDO + 1, FONDO - 1, FONDO - 1);
		// la nave: abierta, de postes de tronco y tejado a dos aguas a lo largo de u
		int a = 6;
		int l = 3;
		int techo = 5;
		o.caja(-a, 0, -l, a, 0, l, Obra.tronco(p.troncoPelado, Direction.Axis.X));
		for (int u = -a; u <= a; u += 3) {
			for (int v : new int[] {-l, l}) {
				o.pilar(u, v, 1, techo - 1);
			}
		}
		o.viga(-a, -l, a, -l, techo);
		o.viga(-a, l, a, l, techo);
		// el banco de la sierra: el tronco entra por una punta, pasa por los cortapiedras y sale en tablas
		for (int u = -a + 1; u <= a - 1; u++) {
			o.pon(u, 1, 0, p.valla);
		}
		o.pon(-a + 1, 2, 0, Obra.tronco(p.tronco, Direction.Axis.X));
		o.pon(-a + 2, 2, 0, Obra.tronco(p.tronco, Direction.Axis.X));
		o.pon(-1, 1, 0, Obra.mira(Blocks.STONECUTTER, Direction.EAST));
		o.pon(1, 1, 0, Obra.mira(Blocks.STONECUTTER, Direction.EAST));
		o.pon(0, 1, 0, Obra.tronco(p.troncoPelado, Direction.Axis.X));
		o.caja(a - 2, 2, 0, a - 1, 2, 0, Obra.losa(p.maderaLosa, false));
		o.pon(-a + 1, 1, -l + 1, Blocks.CRAFTING_TABLE);
		o.pon(-a + 2, 1, -l + 1, Blocks.FLETCHING_TABLE);
		o.pon(a - 1, 1, l - 1, Obra.mira(Blocks.CHEST, Direction.WEST));
		o.caja(a - 3, 1, l - 1, a - 2, 2, l - 1, p.tablas);
		o.pon(0, techo - 1, -1, Obra.farolColgado());
		o.dosAguas(-a - 1, -l - 1, a + 1, l + 1, techo + 1, false, null);
		// las pilas de troncos detrás, tumbados y en pirámide
		pila(o, -7, 6, Direction.Axis.X, p.tronco);
		pila(o, -1, 6, Direction.Axis.X, p.troncoPelado);
		// la carbonera: un montón cubierto de tierra que humea, con el carbón dentro
		int cu = 6;
		int cv = 6;
		for (int du = -2; du <= 2; du++) {
			for (int dv = -2; dv <= 2; dv++) {
				int h = 2 - Math.max(Math.abs(du), Math.abs(dv)) + (Math.abs(du) + Math.abs(dv) <= 1 ? 1 : 0);
				for (int y = 1; y <= h; y++) {
					boolean dentro = Math.abs(du) <= 1 && Math.abs(dv) <= 1 && y < h;
					o.pon(cu + du, y, cv + dv, dentro ? Blocks.COAL_BLOCK : o.azar(du, y, dv, 3) == 0 ? Blocks.COARSE_DIRT : Blocks.PODZOL);
				}
			}
		}
		o.pon(cu, 4, cv, Blocks.CAMPFIRE);
		o.pon(cu + 3, 1, cv - 2, Obra.tronco(p.tronco, Direction.Axis.Z));
		o.pon(cu - 3, 1, cv - 3, Blocks.COMPOSTER);
	}

	/** Pila de troncos tumbados (eje) desde (u, v): tres de ancho abajo, dos encima y uno arriba, de 4 de largo. */
	private static void pila(Obra o, int u, int v, Direction.Axis eje, Block tronco) {
		for (int y = 1; y <= 3; y++) {
			for (int k = 0; k <= 3 - y; k++) {
				for (int largo = 0; largo < 4; largo++) {
					int uu = eje == Direction.Axis.X ? u + largo : u + k + (y - 1);
					int vv = eje == Direction.Axis.X ? v + k : v + largo;
					o.pon(uu, y, vv, Obra.tronco(tronco, eje));
				}
			}
		}
	}

	// ---- el puerto: la lonja ----

	private void lonja(Obra o) {
		Paleta p = o.p;
		int a = 7;
		int l = 5;
		int techo = 5;
		o.planta(-a, -l, a, l + 3);
		o.caja(-a, 0, -l, a, 0, l, p.losa);
		// arcadas de piedra en todo el contorno, abiertas a la plaza
		for (int u = -a; u <= a; u++) {
			for (int v = -l; v <= l; v++) {
				boolean bordeU = Math.abs(u) == a;
				boolean bordeV = Math.abs(v) == l;
				if (!bordeU && !bordeV) {
					continue;
				}
				boolean pilar = (bordeU ? Math.floorMod(v + l, 5) == 0 : Math.floorMod(u + a, 7) == 0) || bordeU && bordeV;
				if (pilar) {
					o.sillares(u, 1, v, u, techo - 1, v, false);
				} else {
					Direction hacia = bordeV ? (Math.floorMod(u + a, 7) <= 3 ? Direction.WEST : Direction.EAST)
							: Math.floorMod(v + l, 5) <= 2 ? Direction.NORTH : Direction.SOUTH;
					boolean junto = bordeV ? Math.floorMod(u + a, 7) == 1 || Math.floorMod(u + a, 7) == 6
							: Math.floorMod(v + l, 5) == 1 || Math.floorMod(v + l, 5) == 4;
					if (junto) {
						o.pon(u, techo - 1, v, Obra.escalon(p.piedraEscalon, hacia, true));
					}
				}
				o.pon(u, techo, v, p.zocalo);
			}
		}
		o.caja(-a + 1, techo, -l + 1, a - 1, techo, l - 1, p.tablas);
		o.dosAguas(-a - 1, -l - 1, a + 1, l + 1, techo + 1, false, p.tablas);
		// los puestos de pescado: mesas con hielo, barriles de salazón, el ahumadero y la báscula
		for (int s = -1; s <= 1; s += 2) {
			int u = 4 * s;
			o.caja(u - 1, 1, -2, u + 1, 1, -2, Blocks.PACKED_ICE);
			o.pon(u, 1, 2, Blocks.BARREL);
			o.pon(u - 1, 1, 2, Blocks.DRIED_KELP_BLOCK);
			o.pon(u + 1, 1, 2, Blocks.BARREL);
		}
		o.pon(0, 1, l - 1, Obra.mira(Blocks.SMOKER, Direction.NORTH));
		o.pon(-1, 1, l - 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		o.pon(1, 1, l - 1, Obra.mira(Blocks.CHEST, Direction.NORTH));
		o.pon(0, 1, 0, Blocks.LECTERN);
		o.pon(0, techo - 1, -2, Obra.farolColgado());
		o.pon(0, techo - 1, 2, Obra.farolColgado());
		// detrás, el secadero de redes: postes con las redes tendidas (telarañas) y una barca en la grada
		for (int u = -a + 1; u <= a - 1; u += 4) {
			o.caja(u, 1, l + 2, u, 3, l + 2, p.valla);
		}
		for (int u = -a + 1; u <= a - 1; u++) {
			o.pon(u, 3, l + 2, u % 4 == 0 ? p.valla : Blocks.COBWEB.defaultBlockState());
		}
	}

	// ---- la aldea: molino de viento y granero ----

	private void molino(Obra o) {
		Paleta p = o.p;
		int t = 3;
		int techo = 11;
		o.planta(-FONDO + 1, -FONDO + 1, FONDO - 1, FONDO - 1);
		// la torre, de esquinas achaflanadas: zócalo de piedra y encima madera
		for (int u = -t; u <= t; u++) {
			for (int v = -t; v <= t; v++) {
				boolean esquina = Math.abs(u) == t && Math.abs(v) == t;
				boolean borde = Math.abs(u) == t || Math.abs(v) == t || Math.abs(u) == t - 1 && Math.abs(v) == t - 1;
				if (esquina) {
					continue;
				}
				o.pon(u, 0, v, p.zocalo);
				if (borde) {
					for (int y = 1; y < techo; y++) {
						o.pon(u, y, v, y <= 3 ? p.piedras[o.azar(u, y, v, p.piedras.length)].defaultBlockState() : p.enlucido);
					}
				}
			}
		}
		o.aire(-t + 1, 1, -t + 1, t - 1, techo - 1, t - 1);
		for (int u : new int[] {-t + 1, t - 1}) {
			for (int v : new int[] {-t + 1, t - 1}) {
				o.caja(u, 4, v, u, techo - 1, v, Obra.tronco(p.tronco, Direction.Axis.Y));
			}
		}
		o.caja(-t + 2, 6, -t + 2, t - 2, 6, t - 2, p.tablas);
		o.aire(t - 2, 6, t - 2, t - 2, 6, t - 2);
		o.puerta(0, 1, -t, Direction.NORTH);
		o.pon(-t, 7, 0, Blocks.GLASS_PANE);
		o.pon(t, 7, 0, Blocks.GLASS_PANE);
		o.pon(0, 3, t, Blocks.GLASS_PANE);
		// dentro: la muela, los sacos de grano y la escala al piso de las velas
		o.pon(0, 1, 0, muela(Direction.NORTH));
		o.caja(0, 2, 0, 0, 5, 0, Obra.tronco(p.tronco, Direction.Axis.Y));
		o.pon(-t + 2, 1, t - 2, Blocks.HAY_BLOCK);
		o.pon(-t + 2, 2, t - 2, Blocks.HAY_BLOCK);
		o.pon(-t + 2, 1, -t + 2, Blocks.COMPOSTER);
		o.caja(t - 2, 1, t - 1, t - 2, 6, t - 1, Obra.mira(Blocks.LADDER, Direction.NORTH));
		o.pon(t - 2, 1, -t + 2, Obra.mira(Blocks.CHEST, Direction.WEST));
		o.chapitel(0, 0, techo, t + 1);
		// las aspas, delante: el eje sale de la fachada y lleva cuatro brazos con su lona
		int ey = 9;
		int ev = -t - 1;
		o.pon(0, ey, ev, Obra.tronco(p.tronco, Direction.Axis.Z));
		for (int k = 1; k <= 5; k++) {
			o.pon(0, ey + k, ev - 1, p.valla);
			o.pon(0, ey - k, ev - 1, p.valla);
			o.pon(k, ey, ev - 1, p.valla);
			o.pon(-k, ey, ev - 1, p.valla);
			if (k >= 2) {
				o.pon(1, ey + k, ev - 1, Blocks.WHITE_WOOL);
				o.pon(-1, ey - k, ev - 1, Blocks.WHITE_WOOL);
				o.pon(k, ey - 1, ev - 1, Blocks.WHITE_WOOL);
				o.pon(-k, ey + 1, ev - 1, Blocks.WHITE_WOOL);
			}
		}
		o.pon(0, ey, ev - 1, Obra.tronco(p.tronco, Direction.Axis.Z));
		// el granero, a un lado: madera, puerta de carro y el heno dentro
		int ua = 4;
		int ub = 8;
		int va = -2;
		int vb = 6;
		o.caja(ua, 0, va, ub, 0, vb, p.tablas);
		o.paredes(ua, 1, va, ub, 4, vb, p.tablas);
		for (int u : new int[] {ua, ub}) {
			for (int v : new int[] {va, vb}) {
				o.caja(u, 1, v, u, 4, v, Obra.tronco(p.tronco, Direction.Axis.Y));
			}
		}
		o.aire(ua + 1, 1, va + 1, ub - 1, 4, vb - 1);
		o.puertaDoble(6, 1, va, Direction.EAST, Direction.NORTH);
		o.toneles(ua + 1, 1, vb - 1, ua + 2, 1, vb - 1);
		o.caja(ub - 1, 1, va + 2, ub - 1, 2, vb - 1, Blocks.HAY_BLOCK);
		o.pon(ua + 1, 1, va + 2, Blocks.COMPOSTER);
		o.pon(ua + 1, 1, va + 3, Obra.mira(Blocks.CHEST, Direction.EAST));
		o.dosAguas(ua - 1, va - 1, ub + 1, vb + 1, 5, true, p.tablas);
	}

	// ---- la ciudad: la taberna ----

	private void taberna(Obra o) {
		Paleta p = o.p;
		int a = 6;
		int l = 6;
		int piso = 5;
		int tejado = 10;
		o.planta(-a, -l, a, l);
		o.caja(-a, 0, -l, a, 0, l, p.zocalo);
		o.caja(-a + 1, 0, -l + 1, a - 1, 0, l - 1, p.tablas);
		o.sillares(-a, 1, -l, a, piso - 1, l, true);
		o.aire(-a + 1, 1, -l + 1, a - 1, tejado - 1, l - 1);
		o.paredes(-a, piso, -l, a, tejado - 1, l, p.enlucido);
		for (int u : new int[] {-a, a}) {
			for (int v : new int[] {-l, l}) {
				o.caja(u, 1, v, u, tejado - 1, v, Obra.tronco(p.tronco, Direction.Axis.Y));
			}
		}
		o.paredes(-a, piso, -l, a, piso, l, Obra.tronco(p.tronco, Direction.Axis.X));
		o.caja(-a + 1, piso, -l + 1, a - 1, piso, l - 1, p.tablas);
		o.puertaDoble(0, 1, -l, Direction.EAST, Direction.NORTH);
		for (int s = -1; s <= 1; s += 2) {
			o.pon(4 * s, 2, -l, Blocks.GLASS_PANE);
			o.pon(3 * s, piso + 2, -l, Blocks.GLASS_PANE);
			o.pon(3 * s, piso + 2, l, Blocks.GLASS_PANE);
			o.pon(-a * s, 2, 2, Blocks.GLASS_PANE);
			o.pon(-a * s, piso + 2, 0, Blocks.GLASS_PANE);
			o.estandarte(2 * s, 3, -l - 1, Direction.NORTH);
		}
		// el letrero de la taberna: un brazo con un farol y un barril colgados sobre la puerta
		o.pon(0, 4, -l - 1, p.valla);
		o.pon(0, 3, -l - 1, Obra.farolColgado());
		// la barra al fondo, con los barriles detrás y el tabernero (puesto del pescador: los barriles)
		for (int u = -a + 2; u <= a - 3; u++) {
			o.pon(u, 1, l - 3, Blocks.BARREL);
			o.pon(u, 2, l - 3, Obra.losa(p.maderaLosa, false));
		}
		o.toneles(-a + 1, 1, l - 1, a - 3, 2, l - 1);
		o.pon(a - 2, 1, l - 2, Blocks.BREWING_STAND);
		o.pon(1, 3, l - 3, Blocks.LANTERN);
		// las mesas, la chimenea y el rincón del juego
		o.mesa(-4, -4, -3, -3, 1, true);
		o.mesa(2, -4, 3, -3, 1, true);
		o.mesa(-4, 0, -3, 0, 1, true);
		o.chimenea(a - 1, 1, 0, Direction.WEST, piso);
		o.silla(a - 3, 1, -1, Direction.EAST);
		o.silla(a - 3, 1, 1, Direction.EAST);
		o.pon(0, 1, 1, Blocks.CAKE);
		// la escalera al piso de las habitaciones, junto a la pared del oeste
		o.escalera(-a + 1, 1, l - 2, Direction.NORTH, piso, p.maderaEscalon, p.tablas);
		int y = piso + 1;
		o.caja(-a + 1, y, 0, a - 1, tejado - 1, 0, p.tablas);
		o.aire(-2, y, 0, -2, y + 1, 0);
		o.aire(2, y, 0, 2, y + 1, 0);
		for (int s = -1; s <= 1; s += 2) {
			o.cama(3 * s, y, l - 1, Direction.SOUTH, Blocks.WHITE_BED);
			o.tonel(4 * s, y, l - 1);
			o.pon(4 * s, y + 1, l - 1, Obra.velas(1));
		}
		o.cama(3, y, -l + 1, Direction.NORTH, Blocks.WHITE_BED);
		o.pon(4, y, -l + 1, Obra.mira(Blocks.CHEST, Direction.SOUTH));
		o.caja(-1, y, -3, 1, y, -2, o.tinte("carpet", Blocks.RED_CARPET));
		o.dosAguas(-a - 1, -l - 1, a + 1, l + 1, tejado, true, p.enlucido);
		o.sillares(a + 1, 0, 0, a + 1, tejado + 3, 0, false);
		o.pon(a + 1, tejado + 4, 0, Blocks.CAMPFIRE);
	}
}
