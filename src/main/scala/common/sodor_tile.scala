package sodor.common

import chisel3._
import chisel3.util._

import freechips.rocketchip.config._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.devices.tilelink._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.diplomaticobjectmodel.logicaltree.{LogicalTreeNode}
import freechips.rocketchip.rocket._
import freechips.rocketchip.subsystem.{RocketCrossingParams}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.util._
import freechips.rocketchip.tile._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.prci.ClockSinkParameters

// Example parameter class copied from Ariane, not included in documentation but for compile check only
// If you are here for documentation, DO NOT copy MyCoreParams and MyTileParams directly - always figure
// out what parameters you need before you write the parameter class
case class SodorCoreParams(
  bootFreqHz: BigInt = BigInt(1700000000),
  ports: Int = 2,
  xprlen: Int = 32,
  internalTile: SodorInternalTileFactory = Stage5Factory
) extends CoreParams {
  val useVM: Boolean = false
  val useHypervisor: Boolean = false
  val useUser: Boolean = false
  val useSupervisor: Boolean = false
  val useDebug: Boolean = true
  val useAtomics: Boolean = false
  val useAtomicsOnlyForIO: Boolean = false // copied from Rocket
  val useCompressed: Boolean = false
  override val useVector: Boolean = false
  val useSCIE: Boolean = false
  val useRVE: Boolean = false
  val mulDiv: Option[MulDivParams] = None
  val fpu: Option[FPUParams] = None
  val nLocalInterrupts: Int = 0
  val useNMI: Boolean = false
  val nPMPs: Int = 0 // TODO: Check
  val pmpGranularity: Int = 4 // copied from Rocket
  val nBreakpoints: Int = 0 // TODO: Check
  val useBPWatch: Boolean = false
  val mcontextWidth: Int = 0 // TODO: Check
  val scontextWidth: Int = 0 // TODO: Check
  val nPerfCounters: Int = 0
  val haveBasicCounters: Boolean = true
  val haveFSDirty: Boolean = false
  val misaWritable: Boolean = false
  val haveCFlush: Boolean = false
  val nL2TLBEntries: Int = 0 // copied from Rocket
  val nL2TLBWays: Int = 0 // copied from Rocket
  val mtvecInit: Option[BigInt] = Some(BigInt(0)) // copied from Rocket
  val mtvecWritable: Boolean = true // copied from Rocket
  val instBits: Int = if (useCompressed) 16 else 32
  val lrscCycles: Int = 80 // copied from Rocket
  val decodeWidth: Int = 1 // TODO: Check
  val fetchWidth: Int = 1 // TODO: Check
  val retireWidth: Int = 1
  val nPTECacheEntries: Int = 0
}

// DOC include start: CanAttachTile
case class SodorTileAttachParams(
  tileParams: SodorTileParams,
  crossingParams: RocketCrossingParams
) extends CanAttachTile {
  type TileType = SodorTile
  val lookup = PriorityMuxHartIdFromSeq(Seq(tileParams))
}
// DOC include end: CanAttachTile

case class SodorTileParams(
  name: Option[String] = Some("sodor_tile"),
  hartId: Int = 0,
  trace: Boolean = false,
  val core: SodorCoreParams = SodorCoreParams(),
  val scratchpad: DCacheParams = DCacheParams()
) extends InstantiableTileParams[SodorTile]
{
  val beuAddr: Option[BigInt] = None
  val blockerCtrlAddr: Option[BigInt] = None
  val btb: Option[BTBParams] = None
  val boundaryBuffers: Boolean = false
  val dcache: Option[DCacheParams] = Some(scratchpad)
  val icache: Option[ICacheParams] = None
  val clockSinkParams: ClockSinkParameters = ClockSinkParameters()
  def instantiate(crossing: TileCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters): SodorTile = {
    new SodorTile(this, crossing, lookup)
  }
}

class SodorTile(
  val sodorParams: SodorTileParams,
  crossing: ClockCrossingType,
  lookup: LookupByHartIdImpl,
  q: Parameters)
  extends BaseTile(sodorParams, crossing, lookup, q)
  with SinksExternalInterrupts
  with SourcesExternalNotifications
{
  // Private constructor ensures altered LazyModule.p is used implicitly
  def this(params: SodorTileParams, crossing: TileCrossingParamsLike, lookup: LookupByHartIdImpl)(implicit p: Parameters) =
    this(params, crossing.crossingType, lookup, p)

  // Require TileLink nodes
  val intOutwardNode = IntIdentityNode()
  val masterNode = visibilityNode
  val slaveNode = TLIdentityNode()

  // Connect node to crossbar switches (bus)
  tlOtherMastersNode := tlMasterXbar.node
  masterNode :=* tlOtherMastersNode
  DisableMonitors { implicit p => tlSlaveXbar.node :*= slaveNode }

  // Slave port adapter
  val coreParams = {
    class C(implicit val p: Parameters) extends HasCoreParameters
    new C
  }
  val dtim_address = tileParams.dcache.flatMap { d => d.scratch.map { s =>
    AddressSet.misaligned(s, d.dataScratchpadBytes)
  }}
  val dtim_adapter = dtim_address.map { addr =>
    LazyModule(new ScratchpadSlavePort(addr, coreParams.coreDataBytes, false))
  }
  dtim_adapter.foreach(lm => connectTLSlave(lm.node, lm.node.portParams.head.beatBytes))

  val dtimProperty = dtim_adapter.map(d => Map(
    "ucb-bar,dtim" -> d.device.asProperty)).getOrElse(Nil)

  // Sodor master port adapter
  val imaster_adapter = if (sodorParams.core.ports == 2) Some(LazyModule(new SodorMasterAdapter()(p, sodorParams.core))) else None
  if (sodorParams.core.ports == 2) tlMasterXbar.node := imaster_adapter.get.node
  val dmaster_adapter = LazyModule(new SodorMasterAdapter()(p, sodorParams.core))
  tlMasterXbar.node := dmaster_adapter.node

  // Implementation class (See below)
  override lazy val module = new SodorTileModuleImp(this)

  // Required entry of CPU device in the device tree for interrupt purpose
  val cpuDevice: SimpleDevice = new SimpleDevice("cpu", Seq("ucb-bar,sodor", "riscv")) {
    override def parent = Some(ResourceAnchors.cpus)
    override def describe(resources: ResourceBindings): Description = {
      val Description(name, mapping) = super.describe(resources)
      Description(name, mapping ++
                        cpuProperties ++
                        nextLevelCacheProperty ++
                        tileProperties ++
                        dtimProperty)
    }
  }

  ResourceBinding {
    Resource(cpuDevice, "reg").bind(ResourceAddress(staticIdForMetadataUseOnly))
  }

  override def makeMasterBoundaryBuffers(crossing: ClockCrossingType)(implicit p: Parameters) = {
    if (!sodorParams.boundaryBuffers) super.makeMasterBoundaryBuffers(crossing)
    else TLBuffer(BufferParams.none, BufferParams.flow, BufferParams.none, BufferParams.flow, BufferParams(1))
  }

  override def makeSlaveBoundaryBuffers(crossing: ClockCrossingType)(implicit p: Parameters) = {
    if (!sodorParams.boundaryBuffers) super.makeSlaveBoundaryBuffers(crossing)
    else TLBuffer(BufferParams.flow, BufferParams.none, BufferParams.none, BufferParams.none, BufferParams.none)
  }

}

class SodorTileModuleImp(outer: SodorTile) extends BaseTileModuleImp(outer){
  // annotate the parameters
  Annotated.params(this, outer.sodorParams)

  // Sodor core parameters
  implicit val conf = outer.sodorParams.core

  // Scratchpad checking
  require(outer.dtim_adapter.isDefined, "Sodor core must have a scratchpad: make sure that tileParams.dcache.scratch is defined.")
  require(outer.dtim_address.get.length == 1, "Sodor core can only have one scratchpad.")

  // Tile
  val tile = Module(outer.sodorParams.core.internalTile.instantiate(outer.dtim_address.get.apply(0)))

  // Add scratchpad adapter
  val scratchpadAdapter = Module(new SodorScratchpadAdapter()(outer.p, conf))
  scratchpadAdapter.io.slavePort <> outer.dtim_adapter.get.module.io.dmem

  // Connect tile
  tile.io.debug_port <> scratchpadAdapter.io.memPort
  tile.io.master_port(0) <> outer.dmaster_adapter.module.io.dport
  if (outer.sodorParams.core.ports == 2) tile.io.master_port(1) <> outer.imaster_adapter.get.module.io.dport

  // Connect interrupts
  outer.decodeCoreInterrupts(tile.io.interrupt)

  // Connect constants
  tile.io.hartid := outer.hartIdSinkNode.bundle
  tile.io.reset_vector := outer.resetVectorSinkNode.bundle
}

class WithNSodorCores(
  n: Int = 1,
  overrideIdOffset: Option[Int] = None,
  internalTile: SodorInternalTileFactory = Stage3Factory()
) extends Config((site, here, up) => {
  case TilesLocated(InSubsystem) => {
    // Calculate the next available hart ID (since hart ID cannot be duplicated)
    val prev = up(TilesLocated(InSubsystem), site)
    require(prev.length == 0, "Sodor doesn't support multiple core.")
    val idOffset = overrideIdOffset.getOrElse(prev.size)
    // Create TileAttachParams for every core to be instantiated
    (0 until n).map { i =>
      SodorTileAttachParams(
        tileParams = SodorTileParams(
          hartId = i + idOffset,
          scratchpad = DCacheParams(
            nSets = 4096, // Very large so we have enough SPAD for bmark tests
            nWays = 1,
            nMSHRs = 0,
            scratch = Some(0x80000000L)
          ),
          core = SodorCoreParams(
            ports = internalTile.nMemPorts,
            internalTile = internalTile
          )
        ),
        crossingParams = RocketCrossingParams()
      )
    } ++ prev
  }
  // Configurate # of bytes in one memory / IO transaction. For RV64, one load/store instruction can transfer 8 bytes at most.
  case SystemBusKey => up(SystemBusKey, site).copy(beatBytes = 4)
  // The # of instruction bits. Use maximum # of bits if your core supports both 32 and 64 bits.
  case XLen => 32
}) {
  require(n == 1, "Sodor doesn't support multiple core.")
}
