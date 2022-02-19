#!/usr/bin/env python3

#for nodes in (1000,):
#  for (c,r) in ((8,8), (4,4), (6,6), (10,10)):
#    b = (c+r)/2  # equivalent to number of bidirectional links
#    for extra_round_trips in (0, 1, 2, 5, 10):
#      for delay in (0, 50, 100, 200, 500, 1000):
#        print('./go cr.cfg NODES=1000 EXTRA_TCP_TRIPS=%i TH=%i TB=%i C=%i R=%i LOGFILE=cr_n1000_c%02i_r%02i_tr%02d_proc%04d' % (extra_round_trips, 0, delay, c, r, c, r, extra_round_trips, delay))
#        for weakest in (2,1):
#          print('./go perigee_last.cfg NODES=1000 EXTRA_TCP_TRIPS=%i TH=%i TB=%i D=%i W=%i LOGFILE=pl_n1000_d%02i_tr%02d_proc%04d_w%01d' % (extra_round_trips, 0, delay, b, weakest, b, extra_round_trips, delay, weakest))
#          print('./go perigee_first.cfg NODES=1000 EXTRA_TCP_TRIPS=%i TH=%i TB=%i D=%i W=%i LOGFILE=pf_n1000_d%02i_tr%02d_proc%04d_w%01d' % (extra_round_trips, 0, delay, b, weakest, b, extra_round_trips, delay, weakest))
#          print('./go perigee_subset.cfg NODES=1000 EXTRA_TCP_TRIPS=%i TH=%i TB=%i D=%i W=%i LOGFILE=ps_n1000_d%02i_tr%02d_proc%04d_w%01d' % (extra_round_trips, 0, delay, b, weakest, b, extra_round_trips, delay, weakest))

for nodes in (1000,):
  for (c,r) in ((8,8), (4,4), (6,6), (10,10)):
    b = (c+r)/2  # equivalent to number of bidirectional links
    for extra_round_trips in (0, 1, 2, 5, 10):
      for delay in (0, 50, 100, 200, 500, 1000):
        print('./go cr.cfg NODES=1000 EXTRA_TCP_TRIPS=%i TH=%i TB=%i C=%i R=%i LOGFILE=crh_n1000_c%02i_r%02i_tr%02d_proc%04d' % (extra_round_trips, 0, delay, c, r, c, r, extra_round_trips, delay))
        for weakest in (2,1):
          print('./go perigee_last.cfg NODES=1000 EXTRA_TCP_TRIPS=%i TH=%i TB=%i D=%i W=%i LOGFILE=plh_n1000_d%02i_tr%02d_proc%04d_w%01d' % (extra_round_trips, 0, delay, b, weakest, b, extra_round_trips, delay, weakest))
          print('./go perigee_first.cfg NODES=1000 EXTRA_TCP_TRIPS=%i TH=%i TB=%i D=%i W=%i LOGFILE=pfh_n1000_d%02i_tr%02d_proc%04d_w%01d' % (extra_round_trips, 0, delay, b, weakest, b, extra_round_trips, delay, weakest))
          print('./go perigee_subset.cfg NODES=1000 EXTRA_TCP_TRIPS=%i TH=%i TB=%i D=%i W=%i LOGFILE=psh_n1000_d%02i_tr%02d_proc%04d_w%01d' % (extra_round_trips, 0, delay, b, weakest, b, extra_round_trips, delay, weakest))

