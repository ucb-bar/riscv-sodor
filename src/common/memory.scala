//**************************************************************************
// Scratchpad Memory (asynchronous)
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2013 Jun 12
//
// Provides a variable number of ports to the core, and one port to the HTIF
// (host-target interface).
//
// Assumes that if the port is ready, it will be performed immediately.
// For now, don't detect write collisions.
//
// Optionally uses synchronous read (default is async). For example, a 1-stage
// processor can only ever work using asynchronous memory!

package Common
{

import chisel3._
import chisel3.util._
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import Constants._

trait MemoryOpConstants 
{
   val MT_X  = 0.asUInt(3.W)
   val MT_B  = 1.asUInt(3.W)
   val MT_H  = 2.asUInt(3.W)
   val MT_W  = 3.asUInt(3.W)
   val MT_D  = 4.asUInt(3.W)
   val MT_BU = 5.asUInt(3.W)
   val MT_HU = 6.asUInt(3.W)
   val MT_WU = 7.asUInt(3.W)

   val M_X   = "b0".U
   val M_XRD = "b0".U // int load
   val M_XWR = "b1".U // int store
}

// from the pov of the datapath
class MemPortIo(data_width: Int)(implicit conf: SodorConfiguration) extends Bundle 
{
   val req    = Decoupled(new MemReq(data_width))
   println("hello")
   val resp   = Flipped(new ValidIO(new MemResp(data_width)))
  override def cloneType = { new MemPortIo(data_width).asInstanceOf[this.type] }
}

class MemReq(data_width: Int)(implicit conf: SodorConfiguration) extends Bundle
{
   val addr = Wire(UInt(conf.xprlen))
   val data = Wire(UInt(data_width))
   val fcn  = Wire(UInt(M_X.getWidth))  // memory function code
   val typ  = Wire(UInt(MT_X.getWidth)) // memory type
  override def cloneType = { new MemReq(data_width).asInstanceOf[this.type] }
}

class MemResp(data_width: Int) extends Bundle
{
   val data = Wire(UInt(data_width))
  override def cloneType = { new MemResp(data_width).asInstanceOf[this.type] }
}

// NOTE: the default is enormous (and may crash your computer), but is bound by
// what the fesvr expects the smallest memory size to be.  A proper fix would
// be to modify the fesvr to expect smaller sizes.
class ScratchPadMemory(num_core_ports: Int, num_bytes: Int = (1 << 21), seq_read: Boolean = false)(implicit conf: SodorConfiguration) extends Module
{
   val io = IO(new Bundle
   {
      val core_ports = Vec.fill(num_core_ports) { (new MemPortIo(data_width = conf.xprlen)).flip }
      val htif_port = (new MemPortIo(data_width = 64)).flip
   })


   // HTIF min packet size is 8 bytes 
   // but 32b core will access in 4 byte chunks
   // thus we will bank the scratchpad
   val num_bytes_per_line = 8
   val num_banks = 2
   val num_lines = num_bytes / num_bytes_per_line
   if (seq_read)
      println("\n    Sodor Tile: creating Synchronous Scratchpad Memory of size " + num_lines*num_bytes_per_line/1024 + " kB\n")
   else
      println("\n    Sodor Tile: creating Asynchronous Scratchpad Memory of size " + num_lines*num_bytes_per_line/1024 + " kB\n")
   val data_bank0 = SeqMem(num_lines, UInt(8*num_bytes_per_line/num_banks))
   val data_bank1 = SeqMem(num_lines, UInt(8*num_bytes_per_line/num_banks))


   // constants
   val idx_lsb = log2Ceil(num_bytes_per_line) 
   val bank_bit = log2Ceil(num_bytes_per_line/num_banks) 

   for (i <- 0 until num_core_ports)
   {
      if (seq_read) 
         io.core_ports(i).resp.valid := Reg(next = io.core_ports(i).req.valid)
      else 
         io.core_ports(i).resp.valid := io.core_ports(i).req.valid
      
      io.core_ports(i).req.ready := Bool(true) // for now, no back pressure 

      val req_valid      = io.core_ports(i).req.valid
      val req_addr       = io.core_ports(i).req.bits.addr
      val req_data       = io.core_ports(i).req.bits.data
      val req_fcn        = io.core_ports(i).req.bits.fcn
      val req_typ        = io.core_ports(i).req.bits.typ
      val byte_shift_amt = io.core_ports(i).req.bits.addr(1,0)
      val bit_shift_amt  = Cat(byte_shift_amt, UInt(0,3))

      // read access
      val r_data_idx = Reg(UInt())
      val r_bank_idx = Reg(Bool())

      val data_idx = req_addr >> UInt(idx_lsb)
      val bank_idx = req_addr(bank_bit)
      val read_data_out = Bits()
      val rdata_out = Bits()

      if (seq_read)
      {
         read_data_out := Mux(r_bank_idx, data_bank1(r_data_idx), data_bank0(r_data_idx))
         rdata_out     := LoadDataGen((read_data_out >> Reg(next=bit_shift_amt)), Reg(next=req_typ))
      }
      else
      {
         read_data_out := Mux(bank_idx, data_bank1(data_idx), data_bank0(data_idx))
         rdata_out     := LoadDataGen((read_data_out >> bit_shift_amt), req_typ)
      }

      io.core_ports(i).resp.bits.data := rdata_out


      // write access
      when (req_valid && req_fcn === M_XWR)
      {
         // move the wdata into position on the sub-line
         val wdata = StoreDataGen(req_data, req_typ) 
         val wmask = (StoreMask(req_typ) << bit_shift_amt)(31,0)

         when (bank_idx)
         {
            data_bank1.write(data_idx, wdata)
         }
         .otherwise
         {
            data_bank0.write(data_idx, wdata)
         }
      }
      .elsewhen (req_valid && req_fcn === M_XRD)
      {
         r_data_idx := data_idx
         r_bank_idx := bank_idx
      }
   }  


   // HTIF -------
   
   io.htif_port.req.ready := Bool(true) // for now, no back pressure
   // synchronous read
   val htif_idx = Reg(UInt())
   htif_idx := io.htif_port.req.bits.addr >> UInt(idx_lsb)
   
   io.htif_port.resp.valid     := Reg(next=io.htif_port.req.valid && io.htif_port.req.bits.fcn === M_XRD)
   io.htif_port.resp.bits.data := Cat(data_bank1(htif_idx), data_bank0(htif_idx))

   when (io.htif_port.req.valid && io.htif_port.req.bits.fcn === M_XWR)
   {
      data_bank0(htif_idx) := io.htif_port.req.bits.data(31,0)
      data_bank1(htif_idx) := io.htif_port.req.bits.data(63,32)
   }

}



object StoreDataGen
{
   def apply(din: Bits, typ: UInt): UInt =
   {
      val word = (typ === MT_W) || (typ === MT_WU)
      val half = (typ === MT_H) || (typ === MT_HU)
      val byte_ = (typ === MT_B) || (typ === MT_BU)

      val dout =  Mux(byte_, Fill(4, din( 7,0)),
                  Mux(half,  Fill(2, din(15,0)),
                             din(31,0)))
      return dout
   }
}


object StoreMask
{
   def apply(sel: UInt): UInt = 
   {
      val mask = Mux(sel === MT_H || sel === MT_HU, Bits(0xffff, 32),
                 Mux(sel === MT_B || sel === MT_BU, Bits(0xff, 32),
                                                    Bits(0xffffffffL, 32)))

      return mask
   }
}

//appropriately mask and sign-extend data for the core
object LoadDataGen
{
   def apply(data: Bits, typ: UInt) : Bits =
   {
      val out = Mux(typ === MT_H,  Cat(Fill(16, data(15)),  data(15,0)),
                Mux(typ === MT_HU, Cat(Fill(16, UInt(0x0)), data(15,0)),
                Mux(typ === MT_B,  Cat(Fill(24, data(7)),    data(7,0)),
                Mux(typ === MT_BU, Cat(Fill(24, UInt(0x0)), data(7,0)), 
                                    data(31,0)))))
      
      return out
   }
}

}
