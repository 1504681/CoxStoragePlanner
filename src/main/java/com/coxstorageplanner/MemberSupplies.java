package com.coxstorageplanner;

/** What one party member last told us they hold. */
public final class MemberSupplies
{
	/** inventory only */
	private final Supplies carried;
	/** null until they open their private storage */
	private final Supplies stored;
	/** null until someone opens the shared storage */
	private final Supplies shared;

	public MemberSupplies(Supplies carried, Supplies stored, Supplies shared)
	{
		this.carried = carried;
		this.stored = stored;
		this.shared = shared;
	}

	public static MemberSupplies from(CoxStorageMessage message)
	{
		return new MemberSupplies(Supplies.fromWire(message.getCarried()),
			message.getStored() == null ? null : Supplies.fromWire(message.getStored()),
			message.getShared() == null ? null : Supplies.fromWire(message.getShared()));
	}

	public Supplies getCarried()
	{
		return carried;
	}

	public Supplies getStored()
	{
		return stored;
	}

	public Supplies getShared()
	{
		return shared;
	}

	/** Inventory plus private storage as far as it is known. */
	public Supplies getHeld()
	{
		return stored == null ? carried : carried.plus(stored);
	}
}
