package es.hulillo.destinos;

import es.hulillo.destinos.politica.Persona;
import java.util.EnumSet;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.7.1 La corte: el cuerpo de una persona de la política (rey, consorte, hijos, general, consejero) en su capital.
 * No se guarda nunca con el mundo (noSave): CorteCapital lo pone cuando hay alguien cerca, lo lleva de sala en sala
 * según la hora (ancla) y lo quita al irse todos. Al cliente solo le llegan dos números: quién es y su aspecto.
 */
public final class CuerpoPersona extends PathfinderMob {
	public static EntityType<CuerpoPersona> TIPO;
	private static final EntityDataAccessor<Integer> PERSONA = SynchedEntityData.defineId(CuerpoPersona.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ASPECTO = SynchedEntityData.defineId(CuerpoPersona.class, EntityDataSerializers.INT);
	/** Si no llega a su sitio en este tiempo (atascado, o sin camino), aparece allí cuando nadie mira. */
	private static final int ATASCO = 20 * 20;
	private static final double CERCA = 1.5;
	/** Nadie a menos de esto: se puede mover de golpe sin que se vea. */
	private static final double NADIE_MIRA = 12;

	/** Dónde tiene que estar ahora (solo en el servidor), o null. */
	private BlockPos ancla;
	/** Si puede dar paseos cortos alrededor del ancla (patios y plaza) o se queda quieto (trono, alcoba…). */
	private boolean pasea;
	/** El reino al que sirve, su cargo en la corte y si lucha (el general) o huye. Los pone CorteCapital. */
	long reino = es.hulillo.destinos.politica.Politica.NADIE;
	RutinaCorte.Cargo cargo = RutinaCorte.Cargo.consejero;
	boolean lucha;

	public CuerpoPersona(EntityType<? extends CuerpoPersona> tipo, Level level) {
		super(tipo, level);
		getNavigation().setCanOpenDoors(true);
		setPersistenceRequired();
	}

	static void registra() {
		Identifier id = Identifier.fromNamespaceAndPath(DestinosCore.MOD_ID, "persona");
		ResourceKey<EntityType<?>> clave = ResourceKey.create(Registries.ENTITY_TYPE, id);
		TIPO = Registry.register(BuiltInRegistries.ENTITY_TYPE, clave, EntityType.Builder.<CuerpoPersona>of(CuerpoPersona::new, MobCategory.MISC)
				.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).noSave().noSummon().build(clave));
		FabricDefaultAttributeRegistry.register(TIPO, PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20)
				.add(Attributes.MOVEMENT_SPEED, 0.5).add(Attributes.FOLLOW_RANGE, 48).add(Attributes.ATTACK_DAMAGE, 5));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		super.defineSynchedData(b);
		b.define(PERSONA, Persona.NADIE);
		b.define(ASPECTO, 0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new Pelea(this));
		goalSelector.addGoal(1, new Huye(this));
		goalSelector.addGoal(2, new OpenDoorGoal(this, true));
		goalSelector.addGoal(3, new VeAlSitio());
		goalSelector.addGoal(4, new Paseo());
		goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8));
		goalSelector.addGoal(6, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new Defiende(this));
	}

	public int persona() {
		return entityData.get(PERSONA);
	}

	public int aspecto() {
		return entityData.get(ASPECTO);
	}

	void fija(int persona, int aspecto) {
		if (persona() != persona) {
			entityData.set(PERSONA, persona);
		}
		if (aspecto() != aspecto) {
			entityData.set(ASPECTO, aspecto);
		}
	}

	/** El sitio de ahora. Si cambia, el camino se busca de nuevo en el siguiente tick de su IA. */
	void ancla(BlockPos a, boolean pasea) {
		if (!a.equals(ancla)) {
			ancla = a;
			getNavigation().stop();
		}
		this.pasea = pasea;
	}

	BlockPos ancla() {
		return ancla;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource fuente, float cantidad) {
		// solo les hacen daño los jugadores (y /kill): ni un zombi ni una caída acaban con un rey
		boolean jugador = fuente.getEntity() instanceof Player;
		if (!jugador && !fuente.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		boolean herido = super.hurtServer(level, fuente, cantidad);
		if (herido && fuente.getEntity() instanceof ServerPlayer p) {
			CorteCapital.golpe(level.getServer(), this, p, isDeadOrDying());
		}
		return herido;
	}

	@Override
	protected InteractionResult mobInteract(Player jugador, InteractionHand mano) {
		if (mano != InteractionHand.MAIN_HAND || !isAlive()) {
			return InteractionResult.PASS;
		}
		if (jugador instanceof ServerPlayer p) {
			DialogoCorte.abre(p, this);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean removeWhenFarAway(double distancia) {
		return false;
	}

	private boolean nadieMira() {
		return level().getNearestPlayer(this, NADIE_MIRA) == null;
	}

	/** Va andando a su sitio; si se atasca o no hay camino, aparece allí cuando nadie mira. */
	private final class VeAlSitio extends Goal {
		private int esperando;
		private int repite;

		VeAlSitio() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return ancla != null && distanceToSqr(Vec3.atBottomCenterOf(ancla)) > CERCA * CERCA;
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void start() {
			esperando = 0;
			repite = 0;
			anda();
		}

		@Override
		public void tick() {
			esperando++;
			if (esperando > ATASCO && nadieMira()) {
				snapTo(ancla.getX() + 0.5, ancla.getY(), ancla.getZ() + 0.5, getYRot(), getXRot());
				getNavigation().stop();
				return;
			}
			// cada segundo: si el camino se ha acabado (o no lo hubo), otro intento
			if (++repite >= 20) {
				repite = 0;
				if (getNavigation().isDone()) {
					anda();
				}
			}
		}

		private void anda() {
			getNavigation().moveTo(ancla.getX() + 0.5, ancla.getY(), ancla.getZ() + 0.5, 0.6);
		}
	}

	/** En los patios y la plaza, de vez en cuando unos pasos alrededor de su sitio. */
	private final class Paseo extends Goal {
		Paseo() {
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return pasea && ancla != null && getNavigation().isDone() && getRandom().nextInt(240) == 0;
		}

		@Override
		public boolean canContinueToUse() {
			return !getNavigation().isDone();
		}

		@Override
		public void start() {
			int dx = getRandom().nextInt(7) - 3;
			int dz = getRandom().nextInt(7) - 3;
			getNavigation().moveTo(ancla.getX() + dx + 0.5, ancla.getY(), ancla.getZ() + dz + 0.5, 0.45);
		}
	}

	/** El general devuelve los golpes. */
	private static final class Pelea extends MeleeAttackGoal {
		private final CuerpoPersona c;

		Pelea(CuerpoPersona c) {
			super(c, 0.9, true);
			this.c = c;
		}

		@Override
		public boolean canUse() {
			return c.lucha && super.canUse();
		}
	}

	/** Los demás salen corriendo. */
	private static final class Huye extends PanicGoal {
		private final CuerpoPersona c;

		Huye(CuerpoPersona c) {
			super(c, 0.9);
			this.c = c;
		}

		@Override
		public boolean canUse() {
			return !c.lucha && super.canUse();
		}
	}

	private static final class Defiende extends HurtByTargetGoal {
		private final CuerpoPersona c;

		Defiende(CuerpoPersona c) {
			super(c);
			this.c = c;
		}

		@Override
		public boolean canUse() {
			return c.lucha && super.canUse();
		}
	}
}
