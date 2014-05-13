#!/usr/bin/perl
use strict;

use FSFW::CLI;

sub main {

    my $cli = FSFW::CLI->new();
    my $success= $cli->login();
    if ($success){
	$cli->terminal_loop();
    }


}


&main;
