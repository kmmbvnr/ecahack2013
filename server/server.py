#!/usr/bin/env python2.7
import os
import sys
import cyclone.web
import cyclone.websocket
import cyclone.escape
from twisted.python import log
from twisted.internet import reactor, task


BASE_PATH = os.path.dirname(__file__)


class Application(cyclone.web.Application):
    def __init__(self):
        fans = {}
        
        handlers = [
             (r"/", MainHandler, dict(fans=fans)),
             (r"/api", APIHandler, dict(fans=fans)),
             (r"/test", TestHandler, dict(fans=fans)),
        ]

        settings = {
            'template_path': os.path.join(BASE_PATH, 'templates'),
            'static_path': os.path.join(BASE_PATH, 'static')
        }

        cyclone.web.Application.__init__(self, handlers, **settings)


class MainHandler(cyclone.web.RequestHandler):
    def initialize(self, fans):
        self.fans = fans

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
    def initialize(self, fans):
        self.fans = fans
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
                # 'active': self.stats.active
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
    
