#!/usr/bin/perl
use strict;
use FindBin; #NONPROD
use lib "$FindBin::Bin/../lib/"; #NONPROD

use FSFW::CLI;

sub main {

    my $cli = FSFW::CLI->new();
    my $success= $cli->login();
    if ($success){
	$cli->terminal_loop();
    }


}


&main;
