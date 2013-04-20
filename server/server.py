#!/usr/bin/env python2.7
import calendar
import os
import sys
import time
import cyclone.web
import cyclone.websocket
import cyclone.escape
from datetime import datetime
from twisted.python import log
from twisted.internet import reactor, task


BASE_PATH = os.path.dirname(__file__)


# http://stackoverflow.com/questions/5067218/get-utc-timestamp-in-python-with-datetime
def utc_mktime(utc_tuple):
    """Returns number of seconds elapsed since epoch

    Note that no timezone are taken into consideration.

    utc tuple must be: (year, month, day, hour, minute, second)

    """
    if len(utc_tuple) == 6:
        utc_tuple += (0, 0, 0)
    return time.mktime(utc_tuple) - time.mktime((1970, 1, 1, 0, 0, 0, 0, 0, 0))


def datetime_to_timestamp(dt):
    """Converts a datetime object to UTC timestamp"""
    return int(utc_mktime(dt.timetuple()))


class Application(cyclone.web.Application):
    def __init__(self):
        fans = {}
        active_fans = {}
        
        handlers = [
             (r"/", MainHandler, dict(fans=fans, active_fans=active_fans)),
             (r"/api", APIHandler, dict(fans=fans, active_fans=active_fans)),
             (r"/test", TestHandler, dict(fans=fans)),
        ]

        settings = {
            'template_path': os.path.join(BASE_PATH, 'templates'),
            'static_path': os.path.join(BASE_PATH, 'static')
        }

        cyclone.web.Application.__init__(self, handlers, **settings)


class MainHandler(cyclone.web.RequestHandler):
    def initialize(self, fans, active_fans):
        self.fans = fans
        self.active_fans = active_fans

    def get(self):
        return self.render("index.html")


class TestHandler(cyclone.web.RequestHandler):
    def initialize(self, fans):
        self.fans = fans

    def get(self):
        return self.render("test.html")


class APIHandler(cyclone.websocket.WebSocketHandler):
    """
    Mobile phone API
    """
    def initialize(self, fans, active_fans):
        self.fans = fans
        self.active_fans = active_fans
        self._stats_updater = None

    def command_register(self, message):
        """
        User place registration
        """
        fan = self.fans[self]
        fan.mobile_id = message['data']['mobile_id']
        fan.sector = int(message['data']['sector'])
        fan.row = int(message['data']['row'])
        fan.place = int(message['data']['place'])
        
        # send stats back
        self._stats_updater = task.LoopingCall(self._sendStats)
        self._stats_updater.start(2)

    def command_activate(self, message):
        self.fans[self].active = True
        self.active_fans[self] = self.fans[self]

    def command_deactivate(self, message):
        self.fans[self].active = False
        del self.active_fans[self]

    def command_timesync(self, message):
        data = {
            'type': 'timesync',
            'data': {
                'sent_time' : message['data']['sent_time'],
                'server_time' : calendar.timegm(datetime.utcnow().utctimetuple())
            }
        }
        self.sendMessage(cyclone.escape.json_encode(data))

    def connectionMade(self):
        self.fans[self] = FunMobile()

    def connectionLost(self, reason):
        del self.fans[self]
        if self._stats_updater:
             self._stats_updater.stop()

    def messageReceived(self, message):
        parsed = cyclone.escape.json_decode(message)
        command = parsed['command']

        cmd_handler= getattr(self, 'command_%s' % command, None)

        if cmd_handler:
            cmd_handler(parsed)
        else:
            log.msg("got unknown command %s" % command)

    def _sendStats(self):
        data = {
            'type': 'stats',
            'data': {
                'users': len(self.fans),
                'active': len(self.active_fans)
            }
        }
        self.sendMessage(cyclone.escape.json_encode(data))


class FunMobile(object):
    def __init__(self, mobile_id=None, sector=None, row=None, 
                 place=None, active=False):
        self.mobile_id = mobile_id
        self.sector = sector
        self.row = row
        self.place = place
        self.active = active
                 

def main():
    reactor.listenTCP(9000, Application())
    reactor.run()


if __name__ == "__main__":
    log.startLogging(sys.stdout)
    main()
    
