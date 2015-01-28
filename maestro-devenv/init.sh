#!/bin/bash
# maestro vagrant bootstrap script
#
#  Initialise enough of an environment to install puppet and complete the
#  configuration of a basic maestro development environment, but don't clobber
#  any existing configuration, since this script may be called again by the
#  vagrant provisioner.
#
#  Before we can run puppet, we need to:
#    1. Test and configure the proxy, if required.
#    3. Add the puppet repositories to apt.
#    4. Update the apt package list.
#    5. Install puppet.
# 

PROXY_ENABLE="yes"
PROXY_HOST="192.168.42.1"
PROXY_PORT="3128"
PROXY="$PROXY_HOST:$PROXY_PORT"
PROXY_HTTP="http://$PROXY"
PROXY_HTTPS="https://$PROXY"
PROXY_APT="/etc/apt/apt.conf.d/120proxy"
PROXY_PROFILE="/etc/profile.d/10proxy"

PUPPET_PKG_URL="https://apt.puppetlabs.com/puppetlabs-release-wheezy.deb"
PUPPET_PKG_NAME="puppetlabs-release-wheezy.deb"

#
# Proxy
#

if [ $PROXY_ENABLE == "yes" ]
then
    echo Configuring proxy:
    
    echo -n "    Testing connectivity... "
    exec 6<>/dev/tcp/$PROXY_HOST/$PROXY_PORT
    E=$?
    if [ $E -eq 0 ]
    then
        echo connection established to $PROXY.
        # Always remember to clean up :)
        exec 6>&-
        exec 6<&-
    else
        echo FAILED to establish a connection to $PROXY \($E\).
        echo Unable to connect to proxy server. Possible causes:
        echo   - Forgot to add 'Gateway yes' to /usr/local/etc/cntlm.conf
        echo   - Forgot to restart cntlm after modifying cntlm.conf
        echo   - Changed network settings in Vagrantfile but not bootstrap.sh
        exit $E
    fi
    
    echo -n "    Configuring system profile... "
    if [ ! -f $PROXY_PROFILE ]
    then
        touch $PROXY_PROFILE
        echo export http_proxy=$PROXY_HTTP >> $PROXY_PROFILE
        echo export https_proxy=$PROXY_HTTPS >> $PROXY_PROFILE
        . $PROXY_PROFILE # We'll need this to wget the puppet repo deb
        echo created $PROXY_PROFILE.
    else
        echo file $PROXY_PROFILE exists, skipping.
    fi
    
    echo -n "    Configuring apt... "
    if [ ! -f $PROXY_APT ]
    then
        touch $PROXY_APT
        echo Acquire::http::Proxy \"$PROXY_HTTP\"; >> $PROXY_APT
        echo Acquire::https::Proxy \"$PROXY_HTTPS\"; >> $PROXY_APT
        echo created $PROXY_APT.
    else
        echo file $PROXY_APT exists, skipping.
    fi
fi

#
# Puppet
#

echo Configuring puppet:

echo -n "    Downloading repository metapackage... "
wget --quiet -P /tmp $PUPPET_PKG_URL
E=$?
if [ $E -eq 0 ]
then
    echo saved to /tmp/$PUPPET_PKG_NAME.
else
    echo FAILED \($E\).
    exit $E
fi

echo -n "    Installing repository metapackage... "
dpkg -i /tmp/$PUPPET_PKG_NAME
E=$?
if [ $E -eq 0 ]
then
    echo installed.
else
    echo FAILED \($E\).
    exit $E
fi

echo -n "    Updating package lists... "
apt-get update
E=$?
if [ $E -eq 0 ]
then
    echo updated.
else
    echo FAILED \($E\).
    exit $E
fi

echo -n "    Installing puppet... "
apt-get -y -qq install puppet > /dev/null
E=$?
if [ $E -eq 0 ]
then
    echo installed.
else
    echo FAILED \($E\).
    exit $E
fi


