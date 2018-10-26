package nvdla

import chisel3._


class NV_NVDLA_CMAC_CORE_active_nvdla1(implicit val conf: cmacConfiguration) extends Module {
    val io = IO(new Bundle {
        //general clock
        val nvdla_core_clk = Input(Clock())
        val nvdla_core_rstn = Input(Bool())

        //config

        val cfg_is_fp16 = Input(Bool())
        val cfg_is_int16 = Input(Bool())
        val cfg_is_int8 = Input(Bool())
        val cfg_reg_en = Input(Bool())
        //input

        

        //control signal
        val in_dat_mask = Input(UInt((conf.CMAC_ATOMC).W))
        val in_dat_pvld = Input(Bool())
        val in_dat_stripe_end = Input(Bool())
        val in_dat_stripe_st = Input(Bool())

        val in_wt_pvld = Input(Bool())
        val in_wt_mask = Input(UInt((conf.CMAC_ATOMC).W))
        val in_wt_sel = Input(UInt((conf.CMAC_ATOMK_HALF).W))


        //data signal
        val in_dat_data = Input(Vec(conf.CMAC_INPUT_NUM, UInt((conf.CMAC_BPE).W)))
        val in_wt_data = Input(Vec(conf.CMAC_INPUT_NUM, UInt((conf.CMAC_BPE).W)))


        //output  
        //dat actv: data nz pvld  pre: mask pvld stripe_end stripe_st 
        //wt acv: data nz pvld  sd: mask pvld

        // full: dat actv: data nan nz pvld  pre: exp mask pvld stripe_end stripe_st
        // wt actv: data nan nz pvld  sd: exp mask pvld


        //EXP = 192
        //PVLD = 104
        //NAN/mask = 64

        //data signal 
        val dat_actv_data =  Output(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.CMAC_BPE*conf.CMAC_ATOMC).W)))
        val wt_actv_data =  Output(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.CMAC_BPE*conf.CMAC_ATOMC).W)))

        //control signal

        val dat_actv_nan = Output(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.NAN).W)))
        val dat_actv_nz = Output(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.CMAC_ATOMC).W)))
        val dat_actv_pvld = Output(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.PVLD).W)))
        val dat_pre_exp =  Output(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.EXP).W)))
        val dat_pre_mask = Output(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.NAN).W)))
        val dat_pre_pvld = Output(Vec(conf.CMAC_ATOMK_HALF, Bool()))
        val dat_pre_stripe_end = Output(Vec(conf.CMAC_ATOMK_HALF, Bool()))   
        val dat_pre_stripe_st = Output(Vec(conf.CMAC_ATOMK_HALF, Bool()))    

        
        val wt_actv_nan = Output(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.NAN).W)))
        val wt_actv_nz = Output(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.CMAC_ATOMC).W)))
        val wt_actv_pvld = Output(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.PVLD).W)))
        
        
        val wt_sd_exp = Output(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.EXP).W)))
        val wt_sd_mask = Output(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.NAN).W)))       
        val wt_sd_pvld = Output(Vec(conf.CMAC_ATOMK_HALF, Bool())) 
    })


    val cfg_is_fp16_d1 = Reg(UInt(98.W))//magic number
    val cfg_is_int16_d1 = Reg(UInt(64.W)) //magic number
    val cfg_is_int8_d1 = Reg(UInt(65.W)) // magic number

    io.dat_actv_data := Reg(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.CMAC_BPE*conf.CMAC_ATOMC).W)))
    io.dat_actv_nan := Reg(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.NAN).W))) 
    io.dat_actv_nz := Reg(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.CMAC_ATOMC).W)))
    io.dat_actv_pvld := Reg(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.PVLD).W)))  

    io.dat_pre_exp := Reg(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.EXP).W)))
    io.dat_pre_mask := Reg(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.NAN).W)))
    io.dat_pre_pvld := Reg(Vec(conf.CMAC_ATOMK_HALF, Bool()))
    io.dat_pre_stripe_end_d = Reg(Vec(conf.CMAC_ATOMK_HALF, Bool())) 
    io.dat_pre_stripe_st = Reg(Vec(conf.CMAC_ATOMK_HALF, Bool()))  

    val dat_actv_data_reg = Reg(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.CMAC_BPE*conf.CMAC_ATOMC).W))) 
    val dat_actv_nan_reg = Reg(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.NAN).W))) 
    val dat_actv_nz_reg = Reg(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.CMAC_ATOMC).W)))
    val dat_actv_pvld = Reg(Vec(conf.CMAC_ATOMK_HALF, UInt((conf.PVLD).W)))

    val dat_actv_stripe_end = Wire(Bool())
    val dat_has_nan = Wire(Bool())

    val dat_pre_data = Reg(UInt((conf.CMAC_BPE*conf.CMAC_ATOMC).W))
    val dat_pre_data_w = Reg(UInt((conf.CMAC_BPE*conf.CMAC_ATOMC).W))


    //initial condition
    withClockAndReset(io.nvdla_core_clk, !io.nvdla_core_rstn) {

        for(t <- 0 to conf.RT_CMAC_A2CACC_LATENCY-1){

            mac2accu_pvld_d(t+1) := RegNext(mac2accu_pvld_d(t))
            mac2accu_mask_d(t+1) := RegNext(mac2accu_mask_d(t))
  
        }
    } 

    //data flight
    withClock(io.nvdla_core_clk) {
        for(t <- 0 to conf.RT_CMAC_A2CACC_LATENCY-1){

            mac2accu_pd_d(t+1) := ShiftRegister(mac2accu_pd_d(t) , 1, mac2accu_pvld_d(t))
            mac2accu_mode_d(t+1) := ShiftRegister(mac2accu_mode_d(t) , 1, mac2accu_pvld_d(t))
            
            for(i <- 0 to conf.CMAC_ATOMK_HALF-1){
            when (mac2accu_mask_d(t)(i)){

                    mac2accu_data_d(t+1)(i)(43,0):= RegNext(mac2accu_data_d(t)(i)(43,0))
                }
                when (mac2accu_mode_d(t)(i)){

                        mac2accu_data_d(t+1)(i)(conf.CMAC_RESULT_WIDTH,44):= RegNext(mac2accu_data_d(t)(i)(conf.CMAC_RESULT_WIDTH,44))

                }       
                
            }
        }
   
    }  

    //output assignment

    io.mac2accu_dst_pvld := mac2accu_pvld_d(2) 
    io.mac2accu_dst_mask := mac2accu_mask_d(2) 
    io.mac2accu_dst_mode := mac2accu_mode_d(2) 
    io.mac2accu_dst_pd := mac2accu_pd_d(2) 
    io.mac2accu_dst_data :=mac2accu_data_d(2) 



  }