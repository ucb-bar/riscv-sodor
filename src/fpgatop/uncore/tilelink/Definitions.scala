// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package uncore.tilelink
import Chisel._
import Common._
import junctions._
import uncore.coherence.CoherencePolicy
import uncore.constants._
import util._
import scala.math.max
import config._

case object CacheBlockOffsetBits extends Field[Int]
case object AmoAluOperandBits extends Field[Int]

case object TLId extends Field[String]
case class TLKey(id: String) extends Field[TileLinkParameters]

/** Parameters exposed to the top-level design, set based on 
  * external requirements or design space exploration
  *
  * Coherency policy used to define custom mesage types
  * Number of manager agents
  * Number of client agents that cache data and use custom [[uncore.Acquire]] types
  * Number of client agents that do not cache data and use built-in [[uncore.Acquire]] types
  * Maximum number of unique outstanding transactions per client
  * Maximum number of clients multiplexed onto a single port
  * Maximum number of unique outstanding transactions per manager
  * Width of cache block addresses
  * Total amount of data per cache block
  * Number of data beats per cache block
  **/

case class TileLinkParameters(
    coherencePolicy: CoherencePolicy,
    nManagers: Int,
    nCachingClients: Int,
    nCachelessClients: Int,
    maxClientXacts: Int,
    maxClientsPerPort: Int,
    maxManagerXacts: Int,
    dataBits: Int,
    dataBeats: Int = 4,
    overrideDataBitsPerBeat: Option[Int] = None
    ) {
  val nClients = nCachingClients + nCachelessClients
  val writeMaskBits: Int  = ((dataBits / dataBeats) - 1) / 8 + 1
  val dataBitsPerBeat: Int = overrideDataBitsPerBeat.getOrElse(dataBits / dataBeats)
}

  
/** Utility trait for building Modules and Bundles that use TileLink parameters */
trait HasTileLinkParameters {
  implicit val p: Parameters
  val tlExternal = p(TLKey(p(TLId)))
  val tlCoh = tlExternal.coherencePolicy
  val tlNManagers = tlExternal.nManagers
  val tlNCachingClients = tlExternal.nCachingClients
  val tlNCachelessClients = tlExternal.nCachelessClients
  val tlNClients = tlExternal.nClients
  val tlClientIdBits =  log2Up(tlNClients)
  val tlManagerIdBits =  log2Up(tlNManagers)
  val tlMaxClientXacts = tlExternal.maxClientXacts
  val tlMaxClientsPerPort = tlExternal.maxClientsPerPort
  val tlMaxManagerXacts = tlExternal.maxManagerXacts
  val tlClientXactIdBits = log2Up(tlMaxClientXacts*tlMaxClientsPerPort)
  val tlManagerXactIdBits = log2Up(tlMaxManagerXacts)
  val tlBlockAddrBits = p(xprlen)
  val tlDataBeats = tlExternal.dataBeats
  val tlDataBits = tlExternal.dataBitsPerBeat
  val tlDataBytes = tlDataBits/8
  val tlWriteMaskBits = tlExternal.writeMaskBits
  val tlBeatAddrBits = log2Up(tlDataBeats)
  val tlByteAddrBits = log2Up(tlWriteMaskBits)
  val tlMemoryOpcodeBits = M_SZ
  val tlMemoryOperandSizeBits = log2Ceil(log2Ceil(tlWriteMaskBits) + 1)
  val tlAcquireTypeBits = max(log2Up(Acquire.nBuiltInTypes), 
                              tlCoh.acquireTypeWidth)
  val tlAcquireUnionBits = max(tlWriteMaskBits,
                                 (tlByteAddrBits +
                                   tlMemoryOperandSizeBits +
                                   tlMemoryOpcodeBits)) + 1
  val tlGrantTypeBits = max(log2Up(Grant.nBuiltInTypes), 
                              tlCoh.grantTypeWidth) + 1
/** Whether the underlying physical network preserved point-to-point ordering of messages */
  val tlNetworkPreservesPointToPointOrdering = false
  val tlNetworkDoesNotInterleaveBeats = true
  val amoAluOperandBits = p(AmoAluOperandBits)
  val amoAluOperandBytes = amoAluOperandBits/8
}

abstract class TLModule(implicit val p: Parameters) extends Module
  with HasTileLinkParameters
abstract class TLBundle(implicit val p: Parameters) extends util.ParameterizedBundle()(p)
  with HasTileLinkParameters

/** Base trait for all TileLink channels */
abstract class TileLinkChannel(implicit p: Parameters) extends TLBundle()(p) {
  def hasData(dummy: Int = 0): Bool
  def hasMultibeatData(dummy: Int = 0): Bool
}
/** Directionality of message channel. Used to hook up logical network ports to physical network ports */
abstract class ClientToManagerChannel(implicit p: Parameters) extends TileLinkChannel()(p)
/** Directionality of message channel. Used to hook up logical network ports to physical network ports */
abstract class ManagerToClientChannel(implicit p: Parameters) extends TileLinkChannel()(p)
/** Directionality of message channel. Used to hook up logical network ports to physical network ports */
abstract class ClientToClientChannel(implicit p: Parameters) extends TileLinkChannel()(p) // Unused for now

/** Common signals that are used in multiple channels.
  * These traits are useful for type parameterizing bundle wiring functions.
  */

/** Address of a cache block. */
trait HasCacheBlockAddress extends HasTileLinkParameters {
  val addr_block = UInt(width = tlBlockAddrBits)

  def conflicts(that: HasCacheBlockAddress) = this.addr_block === that.addr_block
  def conflicts(addr: UInt) = this.addr_block === addr
}

/** Sub-block address or beat id of multi-beat data */
trait HasTileLinkBeatId extends HasTileLinkParameters {
  val addr_beat = UInt(width = tlBeatAddrBits)
}

/* Client-side transaction id. Usually Miss Status Handling Register File index */
trait HasClientTransactionId extends HasTileLinkParameters {
  val client_xact_id = Bits(width = tlClientXactIdBits)
}

/** Manager-side transaction id. Usually Transaction Status Handling Register File index. */
trait HasManagerTransactionId extends HasTileLinkParameters {
  val manager_xact_id = Bits(width = tlManagerXactIdBits)
}

/** A single beat of cache block data */
trait HasTileLinkData extends HasTileLinkBeatId {
  val data = UInt(width = tlDataBits)

  def hasData(dummy: Int = 0): Bool
  def hasMultibeatData(dummy: Int = 0): Bool
  def first(dummy: Int = 0): Bool = !hasMultibeatData() ||  addr_beat === UInt(0)
  def last(dummy: Int = 0): Bool = !hasMultibeatData() || addr_beat === UInt(tlDataBeats-1)
}

/** An entire cache block of data */
trait HasTileLinkBlock extends HasTileLinkParameters {
  val data_buffer = Vec(tlDataBeats, UInt(width = tlDataBits))
  val wmask_buffer = Vec(tlDataBeats, UInt(width = tlWriteMaskBits))
}

/** The id of a client source or destination. Used in managers. */
trait HasClientId extends HasTileLinkParameters {
  val client_id = UInt(width = tlClientIdBits)
}

trait HasManagerId extends HasTileLinkParameters {
  val manager_id = UInt(width = tlManagerIdBits)
}

trait HasAcquireUnion extends HasTileLinkParameters {
  val union = Bits(width = tlAcquireUnionBits)

  // Utility funcs for accessing subblock union:
  def isBuiltInType(t: UInt): Bool
  val opCodeOff = 1
  val opSizeOff = tlMemoryOpcodeBits + opCodeOff
  val addrByteOff = tlMemoryOperandSizeBits + opSizeOff
  val addrByteMSB = tlByteAddrBits + addrByteOff
  /** Hint whether to allocate the block in any interveneing caches */
  def allocate(dummy: Int = 0) = union(0)
  /** Op code for [[uncore.PutAtomic]] operations */
  def op_code(dummy: Int = 0) = Mux(
    isBuiltInType(Acquire.putType) || isBuiltInType(Acquire.putBlockType),
    M_XWR, union(opSizeOff-1, opCodeOff))
  /** Operand size for [[uncore.PutAtomic]] */
  def op_size(dummy: Int = 0) = union(addrByteOff-1, opSizeOff)
  /** Byte address for [[uncore.PutAtomic]] operand */
  def addr_byte(dummy: Int = 0) = union(addrByteMSB-1, addrByteOff)
  def amo_offset(dummy: Int = 0) =
    if (tlByteAddrBits > log2Up(amoAluOperandBytes)) addr_byte()(tlByteAddrBits-1, log2Up(amoAluOperandBytes))
    else UInt(0)
  /** Bit offset of [[uncore.PutAtomic]] operand */
  def amo_shift_bytes(dummy: Int = 0) = UInt(amoAluOperandBytes)*amo_offset()
  /** Write mask for [[uncore.Put]], [[uncore.PutBlock]], [[uncore.PutAtomic]] */
  def wmask(dummy: Int = 0): UInt = {
    val is_amo   = isBuiltInType(Acquire.putAtomicType)
    val amo_mask = if (tlByteAddrBits > log2Up(amoAluOperandBytes))
                     FillInterleaved(amoAluOperandBytes, UIntToOH(amo_offset()))
                   else Acquire.fullWriteMask
    val is_put   = isBuiltInType(Acquire.putBlockType) || isBuiltInType(Acquire.putType)
    val put_mask = union(tlWriteMaskBits, 1)
    Mux(is_amo, amo_mask, Mux(is_put, put_mask, UInt(0)))
  }
  /** Full, beat-sized writemask */
  def full_wmask(dummy: Int = 0) = FillInterleaved(8, wmask())

  /** Is this message a built-in read message */
  def hasPartialWritemask(dummy: Int = 0): Bool = wmask() =/= Acquire.fullWriteMask

}

trait HasAcquireType extends HasTileLinkParameters {
  val is_builtin_type = Bool()
  val a_type = UInt(width = tlAcquireTypeBits)

  /** Message type equality */
  def is(t: UInt) = a_type === t //TODO: make this more opaque; def ===?

  /** Is this message a built-in or custom type */
  def isBuiltInType(dummy: Int = 0): Bool = is_builtin_type
  /** Is this message a particular built-in type */
  def isBuiltInType(t: UInt): Bool = is_builtin_type && a_type === t 

  /** Does this message refer to subblock operands using info in the Acquire.union subbundle */ 
  def isSubBlockType(dummy: Int = 0): Bool = isBuiltInType() && a_type.isOneOf(Acquire.typesOnSubBlocks)

  /** Is this message a built-in prefetch message */
  def isPrefetch(dummy: Int = 0): Bool = isBuiltInType() &&
                                           (is(Acquire.getPrefetchType) || is(Acquire.putPrefetchType))

  /** Is this message a built-in atomic message */
  def isAtomic(dummy: Int = 0): Bool = isBuiltInType() && is(Acquire.putAtomicType)

  /** Is this message a built-in read message */
  def isGet(dummy: Int = 0): Bool = isBuiltInType() && (is(Acquire.getType) || is(Acquire.getBlockType))

  /** Does this message contain data? Assumes that no custom message types have data. */
  def hasData(dummy: Int = 0): Bool = isBuiltInType() && a_type.isOneOf(Acquire.typesWithData)

  /** Does this message contain multiple beats of data? Assumes that no custom message types have data. */
  def hasMultibeatData(dummy: Int = 0): Bool = Bool(tlDataBeats > 1) && isBuiltInType() &&
                                           a_type.isOneOf(Acquire.typesWithMultibeatData)

  /** Mapping between each built-in Acquire type and a built-in Grant type.  */
  def getBuiltInGrantType(dummy: Int = 0): UInt = Acquire.getBuiltInGrantType(this.a_type)
}

trait HasProbeType extends HasTileLinkParameters {
  val p_type = UInt(width = tlCoh.probeTypeWidth)

  def is(t: UInt) = p_type === t
  def hasData(dummy: Int = 0) = Bool(false)
  def hasMultibeatData(dummy: Int = 0) = Bool(false)
}

trait MightBeVoluntary {
  def isVoluntary(dummy: Int = 0): Bool
}

trait HasReleaseType extends HasTileLinkParameters with MightBeVoluntary {
  val voluntary = Bool()
  val r_type = UInt(width = tlCoh.releaseTypeWidth)

  def is(t: UInt) = r_type === t
  def hasData(dummy: Int = 0) = r_type.isOneOf(tlCoh.releaseTypesWithData)
  def hasMultibeatData(dummy: Int = 0) = Bool(tlDataBeats > 1) &&
                                           r_type.isOneOf(tlCoh.releaseTypesWithData)
  def isVoluntary(dummy: Int = 0) = voluntary
  def requiresAck(dummy: Int = 0) = !Bool(tlNetworkPreservesPointToPointOrdering)
}

trait HasGrantType extends HasTileLinkParameters with MightBeVoluntary {
  val is_builtin_type = Bool()
  val g_type = UInt(width = tlGrantTypeBits)

  // Helper funcs
  def isBuiltInType(dummy: Int = 0): Bool = is_builtin_type
  def isBuiltInType(t: UInt): Bool = is_builtin_type && g_type === t 
  def is(t: UInt):Bool = g_type === t
  def hasData(dummy: Int = 0): Bool = Mux(isBuiltInType(),
                                        g_type.isOneOf(Grant.typesWithData),
                                        g_type.isOneOf(tlCoh.grantTypesWithData))
  def hasMultibeatData(dummy: Int = 0): Bool = 
    Bool(tlDataBeats > 1) && Mux(isBuiltInType(),
                               g_type.isOneOf(Grant.typesWithMultibeatData),
                               g_type.isOneOf(tlCoh.grantTypesWithData))
  def isVoluntary(dummy: Int = 0): Bool = isBuiltInType() && (g_type === Grant.voluntaryAckType)
  def requiresAck(dummy: Int = 0): Bool = !Bool(tlNetworkPreservesPointToPointOrdering) && !isVoluntary()
}

/** TileLink channel bundle definitions */

/** The Acquire channel is used to intiate coherence protocol transactions in
  * order to gain access to a cache block's data with certain permissions
  * enabled. Messages sent over this channel may be custom types defined by
  * a [[uncore.CoherencePolicy]] for cached data accesse or may be built-in types
  * used for uncached data accesses. Acquires may contain data for Put or
  * PutAtomic built-in types. After sending an Acquire, clients must
  * wait for a manager to send them a [[uncore.Grant]] message in response.
  */
class AcquireMetadata(implicit p: Parameters) extends ClientToManagerChannel
    with HasCacheBlockAddress 
    with HasClientTransactionId
    with HasTileLinkBeatId
    with HasAcquireType
    with HasAcquireUnion {
  /** Complete physical address for block, beat or operand */
  def full_addr(dummy: Int = 0) =
    Cat(this.addr_block, this.addr_beat,
      Mux(isBuiltInType() && this.a_type.isOneOf(Acquire.typesWithAddrByte),
        this.addr_byte(), UInt(0, tlByteAddrBits)))
}

/** [[uncore.AcquireMetadata]] with an extra field containing the data beat */
class Acquire(implicit p: Parameters) extends AcquireMetadata
  with HasTileLinkData

/** [[uncore.AcquireMetadata]] with an extra field containing the entire cache block */
class BufferedAcquire(implicit p: Parameters) extends AcquireMetadata
  with HasTileLinkBlock

/** [[uncore.Acquire]] with an extra field stating its source id */
class AcquireFromSrc(implicit p: Parameters) extends Acquire
  with HasClientId

/** [[uncore.BufferedAcquire]] with an extra field stating its source id */
class BufferedAcquireFromSrc(implicit p: Parameters) extends BufferedAcquire
  with HasClientId 

/** Used to track metadata for transactions where multiple secondary misses have been merged
  * and handled by a single transaction tracker.
  */
class SecondaryMissInfo(implicit p: Parameters) extends TLBundle
  with HasClientTransactionId
  with HasTileLinkBeatId
  with HasClientId
  with HasAcquireType

/** Contains definitions of the the built-in Acquire types and a factory
  * for [[uncore.Acquire]]
  *
  * In general you should avoid using this factory directly and use
  * [[uncore.ClientMetadata.makeAcquire]] for custom cached Acquires and
  * [[uncore.Get]], [[uncore.Put]], etc. for built-in uncached Acquires.
  *
  * @param is_builtin_type built-in or custom type message?
  * @param a_type built-in type enum or custom type enum
  * @param client_xact_id client's transaction id
  * @param addr_block address of the cache block
  * @param addr_beat sub-block address (which beat)
  * @param data data being put outwards
  * @param union additional fields used for uncached types
  */
object Acquire {
  val nBuiltInTypes = 7
  //TODO: Use Enum
  def getType         = UInt("b000") // Get a single beat of data
  def getBlockType    = UInt("b001") // Get a whole block of data
  def putType         = UInt("b010") // Put a single beat of data
  def putBlockType    = UInt("b011") // Put a whole block of data
  def putAtomicType   = UInt("b100") // Perform an atomic memory op
  def getPrefetchType = UInt("b101") // Prefetch a whole block of data
  def putPrefetchType = UInt("b110") // Prefetch a whole block of data, with intent to write
  def typesWithData = Vec(putType, putBlockType, putAtomicType)
  def typesWithMultibeatData = Vec(putBlockType)
  def typesOnSubBlocks = Vec(putType, getType, putAtomicType)
  def typesWithAddrByte = Vec(getType, putAtomicType)

  /** Mapping between each built-in Acquire type and a built-in Grant type. */
  def getBuiltInGrantType(a_type: UInt): UInt = {
    MuxLookup(a_type, Grant.putAckType, Array(
      Acquire.getType       -> Grant.getDataBeatType,
      Acquire.getBlockType  -> Grant.getDataBlockType,
      Acquire.putType       -> Grant.putAckType,
      Acquire.putBlockType  -> Grant.putAckType,
      Acquire.putAtomicType -> Grant.getDataBeatType,
      Acquire.getPrefetchType -> Grant.prefetchAckType,
      Acquire.putPrefetchType -> Grant.prefetchAckType))
  }

  def makeUnion(
        a_type: UInt,
        addr_byte: UInt,
        operand_size: UInt,
        opcode: UInt,
        wmask: UInt,
        alloc: Bool)
      (implicit p: Parameters): UInt = {
        
    val tlExternal = p(TLKey(p(TLId)))
    val tlWriteMaskBits = tlExternal.writeMaskBits
    val tlByteAddrBits = log2Up(tlWriteMaskBits)
    val tlMemoryOperandSizeBits = log2Ceil(log2Ceil(tlWriteMaskBits) + 1)
    
    // These had better be the right size when we cat them together!
    val my_addr_byte = (UInt(0, tlByteAddrBits) | addr_byte)(tlByteAddrBits-1, 0)
    val my_operand_size = (UInt(0, tlMemoryOperandSizeBits) | operand_size)(tlMemoryOperandSizeBits-1, 0)
    val my_opcode = (UInt(0, M_SZ) | opcode)(M_SZ-1, 0)
    val my_wmask = (UInt(0, tlWriteMaskBits) | wmask)(tlWriteMaskBits-1, 0)
    
    MuxLookup(a_type, UInt(0), Array(
      Acquire.getType       -> Cat(my_addr_byte, my_operand_size, my_opcode, alloc),
      Acquire.getBlockType  -> Cat(my_operand_size, my_opcode, alloc),
      Acquire.putType       -> Cat(my_wmask, alloc),
      Acquire.putBlockType  -> Cat(my_wmask, alloc),
      Acquire.putAtomicType -> Cat(my_addr_byte, my_operand_size, my_opcode, alloc),
      Acquire.getPrefetchType -> Cat(M_XRD, alloc),
      Acquire.putPrefetchType -> Cat(M_XWR, alloc)))
  }

  def fullWriteMask(implicit p: Parameters) = SInt(-1, width = p(TLKey(p(TLId))).writeMaskBits).asUInt
  def fullOperandSize(implicit p: Parameters) = {
    val dataBits = p(TLKey(p(TLId))).dataBitsPerBeat
    UInt(log2Ceil(dataBits / 8))
  }

  // Most generic constructor
  def apply(
        is_builtin_type: Bool,
        a_type: Bits,
        client_xact_id: UInt,
        addr_block: UInt,
        addr_beat: UInt = UInt(0),
        data: UInt = UInt(0),
        union: UInt = UInt(0))
      (implicit p: Parameters): Acquire = {
    val acq = Wire(new Acquire)
    acq.is_builtin_type := is_builtin_type
    acq.a_type := a_type
    acq.client_xact_id := client_xact_id
    acq.addr_block := addr_block
    acq.addr_beat := addr_beat
    acq.data := data
    acq.union := union
    acq
  }

  // Copy constructor
  def apply(a: Acquire): Acquire = {
    val acq = Wire(new Acquire()(a.p))
    acq := a
    acq
  }
}

object BuiltInAcquireBuilder {
  def apply(
        a_type: UInt,
        client_xact_id: UInt,
        addr_block: UInt,
        addr_beat: UInt = UInt(0),
        data: UInt = UInt(0),
        addr_byte: UInt = UInt(0),
        operand_size: UInt = UInt(0),
        opcode: UInt = UInt(0),
        wmask: UInt = UInt(0),
        alloc: Bool = Bool(true))
      (implicit p: Parameters): Acquire = {
    Acquire(
        is_builtin_type = Bool(true),
        a_type = a_type,
        client_xact_id = client_xact_id,
        addr_block = addr_block,
        addr_beat = addr_beat,
        data = data,
        union = Acquire.makeUnion(a_type, addr_byte, operand_size, opcode, wmask, alloc))
  }
}

/** Get a single beat of data from the outer memory hierarchy
  *
  * The client can hint whether he block containing this beat should be 
  * allocated in the intervening levels of the hierarchy.
  *
  * @param client_xact_id client's transaction id
  * @param addr_block address of the cache block
  * @param addr_beat sub-block address (which beat)
  * @param addr_byte sub-block address (which byte)
  * @param operand_size {byte, half, word, double} from [[uncore.MemoryOpConstants]]
  * @param alloc hint whether the block should be allocated in intervening caches
  */
object Get {
  def apply(
        client_xact_id: UInt,
        addr_block: UInt,
        addr_beat: UInt,
        alloc: Bool = Bool(true))
      (implicit p: Parameters): Acquire = {
    BuiltInAcquireBuilder(
      a_type = Acquire.getType,
      client_xact_id = client_xact_id,
      addr_block = addr_block,
      addr_beat = addr_beat,
      operand_size = Acquire.fullOperandSize,
      opcode = M_XRD,
      alloc = alloc)
  }
  def apply(
        client_xact_id: UInt,
        addr_block: UInt,
        addr_beat: UInt,
        addr_byte: UInt,
        operand_size: UInt,
        alloc: Bool)
      (implicit p: Parameters): Acquire = {
    BuiltInAcquireBuilder(
      a_type = Acquire.getType,
      client_xact_id = client_xact_id,
      addr_block = addr_block,
      addr_beat = addr_beat,
      addr_byte = addr_byte, 
      operand_size = operand_size,
      opcode = M_XRD,
      alloc = alloc)
  }
}

/** Get a whole cache block of data from the outer memory hierarchy
  *
  * The client can hint whether the block should be allocated in the 
  * intervening levels of the hierarchy.
  *
  * @param client_xact_id client's transaction id
  * @param addr_block address of the cache block
  * @param alloc hint whether the block should be allocated in intervening caches
  */
object GetBlock {
  def apply(
        client_xact_id: UInt = UInt(0),
        addr_block: UInt,
        alloc: Bool = Bool(true))
      (implicit p: Parameters): Acquire = {
    BuiltInAcquireBuilder(
      a_type = Acquire.getBlockType,
      client_xact_id = client_xact_id, 
      addr_block = addr_block,
      operand_size = Acquire.fullOperandSize,
      opcode = M_XRD,
      alloc = alloc)
  }
}

/** Prefetch a cache block into the next-outermost level of the memory hierarchy
  * with read permissions.
  *
  * @param client_xact_id client's transaction id
  * @param addr_block address of the cache block
  */
object GetPrefetch {
  def apply(
       client_xact_id: UInt,
       addr_block: UInt)
      (implicit p: Parameters): Acquire = {
    BuiltInAcquireBuilder(
      a_type = Acquire.getPrefetchType,
      client_xact_id = client_xact_id,
      addr_block = addr_block)
  }
}

/** Put a single beat of data into the outer memory hierarchy
  *
  * The block will be allocated in the next-outermost level of the hierarchy.
  *
  * @param client_xact_id client's transaction id
  * @param addr_block address of the cache block
  * @param addr_beat sub-block address (which beat)
  * @param data data being refilled to the original requestor
  * @param wmask per-byte write mask for this beat
  * @param alloc hint whether the block should be allocated in intervening caches
  */
object Put {
  def apply(
        client_xact_id: UInt,
        addr_block: UInt,
        addr_beat: UInt,
        data: UInt,
        wmask: Option[UInt]= None,
        alloc: Bool = Bool(true))
      (implicit p: Parameters): Acquire = {
    BuiltInAcquireBuilder(
      a_type = Acquire.putType,
      addr_block = addr_block,
      addr_beat = addr_beat,
      client_xact_id = client_xact_id,
      data = data,
      wmask = wmask.getOrElse(Acquire.fullWriteMask),
      alloc = alloc)
  }
}

/** Put a whole cache block of data into the outer memory hierarchy
  *
  * If the write mask is not full, the block will be allocated in the
  * next-outermost level of the hierarchy. If the write mask is full, the
  * client can hint whether the block should be allocated or not.
  *
  * @param client_xact_id client's transaction id
  * @param addr_block address of the cache block
  * @param addr_beat sub-block address (which beat of several)
  * @param data data being refilled to the original requestor
  * @param wmask per-byte write mask for this beat
  * @param alloc hint whether the block should be allocated in intervening caches
  */
object PutBlock {
  def apply(
        client_xact_id: UInt,
        addr_block: UInt,
        addr_beat: UInt,
        data: UInt,
        wmask: Option[UInt] = None,
        alloc: Bool = Bool(true))
      (implicit p: Parameters): Acquire = {
    BuiltInAcquireBuilder(
      a_type = Acquire.putBlockType,
      client_xact_id = client_xact_id,
      addr_block = addr_block,
      addr_beat = addr_beat,
      data = data,
      wmask = wmask.getOrElse(Acquire.fullWriteMask),
      alloc = alloc)
  }
}

/** Prefetch a cache block into the next-outermost level of the memory hierarchy
  * with write permissions.
  *
  * @param client_xact_id client's transaction id
  * @param addr_block address of the cache block
  */
object PutPrefetch {
  def apply(
        client_xact_id: UInt,
        addr_block: UInt)
      (implicit p: Parameters): Acquire = {
    BuiltInAcquireBuilder(
      a_type = Acquire.putPrefetchType,
      client_xact_id = client_xact_id,
      addr_block = addr_block)
  }
}

/** Perform an atomic memory operation in the next-outermost level of the memory hierarchy
  *
  * @param client_xact_id client's transaction id
  * @param addr_block address of the cache block
  * @param addr_beat sub-block address (within which beat)
  * @param addr_byte sub-block address (which byte)
  * @param atomic_opcode {swap, add, xor, and, min, max, minu, maxu} from [[uncore.MemoryOpConstants]]
  * @param operand_size {byte, half, word, double} from [[uncore.MemoryOpConstants]]
  * @param data source operand data
  */
object PutAtomic {
  def apply(
        client_xact_id: UInt,
        addr_block: UInt,
        addr_beat: UInt,
        addr_byte: UInt,
        atomic_opcode: UInt,
        operand_size: UInt,
        data: UInt)
      (implicit p: Parameters): Acquire = {
    BuiltInAcquireBuilder(
      a_type = Acquire.putAtomicType,
      client_xact_id = client_xact_id, 
      addr_block = addr_block, 
      addr_beat = addr_beat, 
      data = data,
      addr_byte = addr_byte,
      operand_size = operand_size,
      opcode = atomic_opcode)
  }
}

/** The Probe channel is used to force clients to release data or cede permissions
  * on a cache block. Clients respond to Probes with [[uncore.Release]] messages.
  * The available types of Probes are customized by a particular
  * [[uncore.CoherencePolicy]].
  */
class Probe(implicit p: Parameters) extends ManagerToClientChannel
  with HasCacheBlockAddress 
  with HasProbeType

/** [[uncore.Probe]] with an extra field stating its destination id */
class ProbeToDst(implicit p: Parameters) extends Probe()(p) with HasClientId

/** Contains factories for [[uncore.Probe]] and [[uncore.ProbeToDst]]
  *
  * In general you should avoid using these factories directly and use
  * [[uncore.ManagerMetadata.makeProbe(UInt,Acquire)* makeProbe]] instead.
  *
  * @param dst id of client to which probe should be sent
  * @param p_type custom probe type
  * @param addr_block address of the cache block
  */
object Probe {
  def apply(p_type: UInt, addr_block: UInt)(implicit p: Parameters): Probe = {
    val prb = Wire(new Probe)
    prb.p_type := p_type
    prb.addr_block := addr_block
    prb
  }
  def apply(dst: UInt, p_type: UInt, addr_block: UInt)(implicit p: Parameters): ProbeToDst = {
    val prb = Wire(new ProbeToDst)
    prb.client_id := dst
    prb.p_type := p_type
    prb.addr_block := addr_block
    prb
  }
}

/** The Release channel is used to release data or permission back to the manager
  * in response to [[uncore.Probe]] messages. It can also be used to voluntarily
  * write back data, for example in the event that dirty data must be evicted on
  * a cache miss. The available types of Release messages are always customized by
  * a particular [[uncore.CoherencePolicy]]. Releases may contain data or may be
  * simple acknowledgements. Voluntary Releases are acknowledged with [[uncore.Grant Grants]].
  */
class ReleaseMetadata(implicit p: Parameters) extends ClientToManagerChannel
    with HasTileLinkBeatId
    with HasCacheBlockAddress 
    with HasClientTransactionId 
    with HasReleaseType {
  def full_addr(dummy: Int = 0) = Cat(this.addr_block, this.addr_beat, UInt(0, width = tlByteAddrBits))
}

/** [[uncore.ReleaseMetadata]] with an extra field containing the data beat */
class Release(implicit p: Parameters) extends ReleaseMetadata
  with HasTileLinkData

/** [[uncore.ReleaseMetadata]] with an extra field containing the entire cache block */
class BufferedRelease(implicit p: Parameters) extends ReleaseMetadata
  with HasTileLinkBlock

/** [[uncore.Release]] with an extra field stating its source id */
class ReleaseFromSrc(implicit p: Parameters) extends Release
  with HasClientId

/** [[uncore.BufferedRelease]] with an extra field stating its source id */
class BufferedReleaseFromSrc(implicit p: Parameters) extends BufferedRelease
  with HasClientId

/** Contains a [[uncore.Release]] factory
  *
  * In general you should avoid using this factory directly and use
  * [[uncore.ClientMetadata.makeRelease]] instead.
  *
  * @param voluntary is this a voluntary writeback
  * @param r_type type enum defined by coherence protocol
  * @param client_xact_id client's transaction id
  * @param addr_block address of the cache block
  * @param addr_beat beat id of the data
  * @param data data being written back
  */
object Release {
  def apply(
        voluntary: Bool,
        r_type: UInt,
        client_xact_id: UInt,
        addr_block: UInt,
        addr_beat: UInt,
        data: UInt)
      (implicit p: Parameters): Release = {
    val rel = Wire(new Release)
    rel.r_type := r_type
    rel.client_xact_id := client_xact_id
    rel.addr_block := addr_block
    rel.addr_beat := addr_beat
    rel.data := data
    rel.voluntary := voluntary
    rel
  }

  def apply(
        src: UInt,
        voluntary: Bool,
        r_type: UInt,
        client_xact_id: UInt,
        addr_block: UInt,
        addr_beat: UInt = UInt(0),
        data: UInt = UInt(0))
      (implicit p: Parameters): ReleaseFromSrc = {
    val rel = Wire(new ReleaseFromSrc)
    rel.client_id := src
    rel.voluntary := voluntary
    rel.r_type := r_type
    rel.client_xact_id := client_xact_id
    rel.addr_block := addr_block
    rel.addr_beat := addr_beat
    rel.data := data
    rel
  }
}

/** The Grant channel is used to refill data or grant permissions requested of the 
  * manager agent via an [[uncore.Acquire]] message. It is also used to acknowledge
  * the receipt of voluntary writeback from clients in the form of [[uncore.Release]]
  * messages. There are built-in Grant messages used for Gets and Puts, and
  * coherence policies may also define custom Grant types. Grants may contain data
  * or may be simple acknowledgements. Grants are responded to with [[uncore.Finish]].
  */
class GrantMetadata(implicit p: Parameters) extends ManagerToClientChannel
    with HasTileLinkBeatId
    with HasClientTransactionId 
    with HasManagerTransactionId
    with HasGrantType {
  def makeFinish(dummy: Int = 0): Finish = {
    val f = Wire(new Finish)
    f.manager_xact_id := this.manager_xact_id
    f
  }
}

/** [[uncore.GrantMetadata]] with an extra field containing a single beat of data */
class Grant(implicit p: Parameters) extends GrantMetadata
  with HasTileLinkData

/** [[uncore.Grant]] with an extra field stating its destination */
class GrantToDst(implicit p: Parameters) extends Grant
  with HasClientId

/** [[uncore.Grant]] with an extra field stating its destination */
class GrantFromSrc(implicit p: Parameters) extends Grant
    with HasManagerId {
  override def makeFinish(dummy: Int = 0): FinishToDst = {
    val f = Wire(new FinishToDst)
    f.manager_xact_id := this.manager_xact_id
    f.manager_id := this.manager_id
    f
  }
}

/** [[uncore.GrantMetadata]] with an extra field containing an entire cache block */
class BufferedGrant(implicit p: Parameters) extends GrantMetadata
  with HasTileLinkBlock

/** [[uncore.BufferedGrant]] with an extra field stating its destination */
class BufferedGrantToDst(implicit p: Parameters) extends BufferedGrant
  with HasClientId

/** Contains definitions of the the built-in grant types and factories 
  * for [[uncore.Grant]] and [[uncore.GrantToDst]]
  *
  * In general you should avoid using these factories directly and use
  * [[uncore.ManagerMetadata.makeGrant(uncore.AcquireFromSrc* makeGrant]] instead.
  *
  * @param dst id of client to which grant should be sent
  * @param is_builtin_type built-in or custom type message?
  * @param g_type built-in type enum or custom type enum
  * @param client_xact_id client's transaction id
  * @param manager_xact_id manager's transaction id
  * @param addr_beat beat id of the data
  * @param data data being refilled to the original requestor
  */
object Grant {
  val nBuiltInTypes = 5
  def voluntaryAckType = UInt("b000") // For acking Releases
  def prefetchAckType  = UInt("b001") // For acking any kind of Prefetch
  def putAckType       = UInt("b011") // For acking any kind of non-prfetch Put
  def getDataBeatType  = UInt("b100") // Supplying a single beat of Get
  def getDataBlockType = UInt("b101") // Supplying all beats of a GetBlock
  def typesWithData = Vec(getDataBlockType, getDataBeatType)
  def typesWithMultibeatData= Vec(getDataBlockType)

  def apply(
        is_builtin_type: Bool,
        g_type: UInt,
        client_xact_id: UInt, 
        manager_xact_id: UInt,
        addr_beat: UInt,
        data: UInt)
      (implicit p: Parameters): Grant = {
    val gnt = Wire(new Grant)
    gnt.is_builtin_type := is_builtin_type
    gnt.g_type := g_type
    gnt.client_xact_id := client_xact_id
    gnt.manager_xact_id := manager_xact_id
    gnt.addr_beat := addr_beat
    gnt.data := data
    gnt
  }

  def apply(
        dst: UInt,
        is_builtin_type: Bool,
        g_type: UInt,
        client_xact_id: UInt,
        manager_xact_id: UInt,
        addr_beat: UInt = UInt(0),
        data: UInt = UInt(0))
      (implicit p: Parameters): GrantToDst = {
    val gnt = Wire(new GrantToDst)
    gnt.client_id := dst
    gnt.is_builtin_type := is_builtin_type
    gnt.g_type := g_type
    gnt.client_xact_id := client_xact_id
    gnt.manager_xact_id := manager_xact_id
    gnt.addr_beat := addr_beat
    gnt.data := data
    gnt
  }
}

/** The Finish channel is used to provide a global ordering of transactions
  * in networks that do not guarantee point-to-point ordering of messages.
  * A Finsish message is sent as acknowledgement of receipt of a [[uncore.Grant]].
  * When a Finish message is received, a manager knows it is safe to begin
  * processing other transactions that touch the same cache block.
  */
class Finish(implicit p: Parameters) extends ClientToManagerChannel()(p)
    with HasManagerTransactionId {
  def hasData(dummy: Int = 0) = Bool(false)
  def hasMultibeatData(dummy: Int = 0) = Bool(false)
}

/** [[uncore.Finish]] with an extra field stating its destination */
class FinishToDst(implicit p: Parameters) extends Finish
  with HasManagerId

/** Complete IO definition for incoherent TileLink, including networking headers */
class UncachedTileLinkIO(implicit p: Parameters) extends TLBundle()(p) {
  val acquire   = new DecoupledIO(new LogicalNetworkIO(new Acquire))
  val grant     = new DecoupledIO(new LogicalNetworkIO(new Grant)).flip
  val finish = new DecoupledIO(new LogicalNetworkIO(new Finish))
}

/** Complete IO definition for coherent TileLink, including networking headers */
class TileLinkIO(implicit p: Parameters) extends UncachedTileLinkIO()(p) {
  val probe     = new DecoupledIO(new LogicalNetworkIO(new Probe)).flip
  val release   = new DecoupledIO(new LogicalNetworkIO(new Release))
}

/** This version of UncachedTileLinkIO does not contain network headers. 
  * It is intended for use within client agents.
  *
  * Headers are provided in the top-level that instantiates the clients and network,
  * probably using a [[uncore.ClientTileLinkNetworkPort]] module.
  * By eliding the header subbundles within the clients we can enable 
  * hierarchical P-and-R while minimizing unconnected port errors in GDS.
  *
  * Secondly, this version of the interface elides [[uncore.Finish]] messages, with the
  * assumption that a [[uncore.FinishUnit]] has been coupled to the TileLinkIO port
  * to deal with acking received [[uncore.Grant Grants]].
  */
class ClientUncachedTileLinkIO(implicit p: Parameters) extends TLBundle()(p) {
  val acquire   = new DecoupledIO(new Acquire)
  val grant     = new DecoupledIO(new Grant).flip
}

/** This version of TileLinkIO does not contain network headers. 
  * It is intended for use within client agents.
  */
class ClientTileLinkIO(implicit p: Parameters) extends TLBundle()(p) {
  val acquire   = new DecoupledIO(new Acquire)
  val probe     = new DecoupledIO(new Probe).flip
  val release   = new DecoupledIO(new Release)
  val grant     = new DecoupledIO(new GrantFromSrc).flip
  val finish    = new DecoupledIO(new FinishToDst)
}

/** This version of TileLinkIO does not contain network headers, but
  * every channel does include an extra client_id subbundle.
  * It is intended for use within Management agents.
  *
  * Managers need to track where [[uncore.Acquire]] and [[uncore.Release]] messages
  * originated so that they can send a [[uncore.Grant]] to the right place. 
  * Similarly they must be able to issues Probes to particular clients.
  * However, we'd still prefer to have [[uncore.ManagerTileLinkNetworkPort]] fill in
  * the header.src to enable hierarchical p-and-r of the managers. Additionally, 
  * coherent clients might be mapped to random network port ids, and we'll leave it to the
  * [[uncore.ManagerTileLinkNetworkPort]] to apply the correct mapping. Managers do need to
  * see Finished so they know when to allow new transactions on a cache
  * block to proceed.
  */
class ManagerTileLinkIO(implicit p: Parameters) extends TLBundle()(p) {
  val acquire   = new DecoupledIO(new AcquireFromSrc).flip
  val grant     = new DecoupledIO(new GrantToDst)
  val finish    = new DecoupledIO(new Finish).flip
  val probe     = new DecoupledIO(new ProbeToDst)
  val release   = new DecoupledIO(new ReleaseFromSrc).flip
}
