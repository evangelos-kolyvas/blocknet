#!/usr/bin/env python3


#for blocksize in (5,10,20,50,100,200,500,1000,2000):
for nodes in (1000,):
  for (latencies,lat_log) in (('latencies/wondernetwork_latencies_oneway_2022-02-08.dat', 'w'), ('latencies/perigee_latencies_weight1.dat', 'p')):
    print('./go cfg/cr.cfg             NODES=1000  C=5 R=5 S=0  init.latencies_matrix.file="%s"  LOGFILE=cr_c5_r5_s0_L%s'   % (latencies, lat_log))
    print('./go cfg/perigee_subset.cfg NODES=1000  C=0 R=2 S=8  init.latencies_matrix.file="%s"  LOGFILE=prg_c0_r2_s8_L%s'  % (latencies, lat_log))
    print('./go cfg/perigee_subset.cfg NODES=1000  C=0 R=1 S=9  init.latencies_matrix.file="%s"  LOGFILE=prg_c0_r1_s9_L%s'  % (latencies, lat_log))
    print('./go cfg/perigee_subset.cfg NODES=1000  C=4 R=2 S=4  init.latencies_matrix.file="%s"  LOGFILE=hybr_c4_r2_s4_L%s' % (latencies, lat_log))
    print('./go cfg/perigee_subset.cfg NODES=1000  C=4 R=1 S=5  init.latencies_matrix.file="%s"  LOGFILE=hybr_c4_r1_s5_L%s' % (latencies, lat_log))

