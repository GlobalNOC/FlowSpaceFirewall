#!/usr/bin/perl

#--------------------------------------------------------------------
#----- FSFW CLI library
#-----
#----- Copyright(C) 2014 The Trustees of Indiana University
#--------------------------------------------------------------------
#----- $HeadURL: 
#----- $Id: 
#-----
#----- cli code
#---------------------------------------------------------------------

package FSFW::CLI;

use Term::ReadLine; #using Term::ReadLine::Gnu
use GRNOC::Config;
use FindBin;
use Data::Dumper;

sub new {

    my $proto = shift;
    my $class = ref($proto) || $proto;
    
    my %attributes = (
	host => undef,
	user => undef,
	password => undef,
	timeout => 30,
	debug => 0,
	@_,
	);

    my $self = \%attributes;
    
    bless ($self,$class);
    warn Dumper ($self);
    $self->_init();
    
    return $self;

}

sub _init {
    my $self = shift;

    #$self->{config} = GRNOC::Config->new() 
    $self->{history}= [];
    $self->{history_index}=0;
    $self->build_command_list();
    $self->{'term'} = Term::ReadLine->new('FSFW CLI');
    $self->set_prompt('FSFW# ');

    #use builtin 'list completion function'
    my $attribs = $self->{'term'}->Attribs;
    #$attribs->{completion_entry_function} = $attribs->{list_completion_function};
    my $cli= $self;
    $attribs->{completion_function} = sub {
	my ($text,$line,$start) = @_;
#	warn "\n\nLINE::$line ";
	$self->command_complete($text,$line,$start);
    };
    #$attribs->{'completion_word'} = ['show flows','show slices', 'show status', 'show switches'];
    #warn Dumper $self->{'term'};
    return;
}

sub command_complete {
    my $self = shift;
    #warn Dumper ($self);
    my ($text,$line,$start) = @_;
    my $command_list = $self->get_command_list();
    my @matches = ();
    #warn Dumper $command_list;
    foreach $command (@$command_list){
	my $offset =0;
	my $is_match=1;
	my @text_parts = split(" ",$line);
	my @command_parts = split (" ",$command);
	my $last_word=$command_parts[0];
#	unless (scalar(@$text_parts) ){
#	    push (@matches,$command_parts[0]);
#	}
	for(my $i=0; $i< scalar(@text_parts); $i++) {
	    #warn "checking if $text_parts[$i] is in $command_parts[$i]\n";
	    unless ($command_parts[$i] =~/^$text_parts[$i].*/){
		#warn "it isn't\n";
		$is_match=0;
		last;
	    }
	    $last_word = $command_parts[$i];
	    if (length($text_parts[$i]) == length($command_parts[$i]) ){
		$last_word=$command_parts[$i+1];
	    }
	    
	    
	}
	if($is_match){
	    #warn "adding $last_word to matches\n";
	    push (@matches,$last_word);
	}
    }
    #warn Dumper (\@matches);
    return @matches;
}

sub login {

    my $self = shift;

    return 1; #NONPROD

}

sub build_command_list {

    my $self = shift;
    $self->{'possible_commands'} = ['show flows','show slices', 'show status', 'show switches']; #NONPROD
    
    
    
}
sub get_command_list {
    my $self = shift;
    return $self->{'possible_commands'};
}

sub set_prompt {
    my $self = shift;
    $self->{'prompt'} = shift;
    return;
}

sub get_prompt {
    my $self = shift;
    return $self->{'prompt'};
}

sub handle_input {
    my $self = shift;
    my $input = shift;
    print "Got Command : $input \n"; #NONPROD
    return;
}

sub terminal_loop {

    my $self = shift;
    #my $process_input = shift;
    #my $command_complete = shift;


    #$self->print_prompt();
    my $line;
    $term = $self->{'term'};

    while ( defined( $line = $term->readline( $self->get_prompt() ) ) ) {

	$self->handle_input($line);

    }
 
    

}

1;
