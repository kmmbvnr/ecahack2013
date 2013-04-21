#!/usr/bin/env python2.7
# -*- encoding: utf-8 -*-
import calendar
import os
import sys
import random
import cyclone.web
import cyclone.websocket
import cyclone.escape
from datetime import datetime
from twisted.python import log
from twisted.internet import reactor, task


BASE_PATH = os.path.dirname(__file__)


def timestamp():
    dt = datetime.utcnow()
    return calendar.timegm(dt.utctimetuple())*1000 + (dt.microsecond/1000)


class Application(cyclone.web.Application):
    def __init__(self):
        fans = {}
        active_fans = {}
        pattern_builder = PatternBuilder(active_fans=active_fans)
        
        handlers = [
             (r"/", MainHandler, dict(fans=fans, active_fans=active_fans)),
             (r"/api", APIHandler, dict(fans=fans, active_fans=active_fans, pattern_builder=pattern_builder)),
             (r"/test", TestHandler, dict(fans=fans)),
        ]

        settings = {
            'template_path': os.path.join(BASE_PATH, 'templates'),
            'static_path': os.path.join(BASE_PATH, 'static')
        }

        cyclone.web.Application.__init__(self, handlers, **settings)


class PatternBuilder(object):
    def __init__(self, active_fans):
        self.active_fans = active_fans
        self.active_timer = None

    def start(self):
        self.stop()
        delay = random.randint(25, 35)
        self.active_timer = reactor.callLater(delay, self.execute)

    def stop(self):
        if self.active_timer:
            if not self.active_timer.called:
                self.active_timer.cancel()
            self.active_timer = None

    def execute(self):
        self.stop()
        pattern_data = self.create_pattern()
        for fun, pattern in pattern_data:
            fun.sendMessage(cyclone.escape.json_encode({ 'type' : 'pattern',
                                                         'data': pattern }))
        self.start()

    def create_pattern(self):
        pattern_num = random.randint(1,3)
        start_at = timestamp() + 5000;
        if pattern_num == 1: 
            """
            Мигаем
            """
            for fun in self.active_fans.keys():
                yield fun, {
                    'pattern_name': u'А-а-а-а-а-а!',
                    'start_at': start_at,
                    'interval': 100,
                    'pattern':  [1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0]
                }
        elif pattern_num == 2:
            """
            Вперед, омичка, мы с тобой
            """
            for n, fun in enumerate(self.active_fans.keys()):
                if (n+1)%3 == 0:
                    template = [1, 1, 0, 0, 0, 0, 0, 0 ]
                elif (n+1)%3 == 1:
                    template = [0, 0, 0, 1, 1, 0, 0, 0 ]
                else:
                    template = [0, 0, 0, 0, 0, 0, 1, 1 ]
                template = [1, 1, 0, 1, 1, 0, 1, 1]
        
                yield fun, {
                    'pattern_name': u'Вперед, омичка, Мы с тобой',
                    'start_at': start_at,
                    'interval': 300,
                    'pattern':  [1, 1, 1, 1, 0, 0, 1, 1, 1, 1, 0] + template
                }
        elif pattern_num == -1:
            """
            Волна
            """
            rows = set([x.row for x in self.active_fans.values()])
            rows = sorted(rows)

            for fun, data in self.active_fans.items():
                template = [0]* len(rows)
                template[rows.index(data.row)] = 1

                yield fun, {
                    'pattern_name': u'Волна',
                    'start_at': start_at,
                    'interval': 500,
                    'pattern':  (template + list(reversed(template))) * 3
                }
        elif pattern_num == 3:
            """
            Тум-тем
            """
            for fun in self.active_fans.keys():
                yield fun, {
                    'pattern_name': u'Там-дам-та-да-дам!',
                    'start_at': start_at,
                    'interval': 200,
                    'pattern':  [1,1,1,0,1,1,1,0,1,0,1,0,1,0,0,1,0,1,0,1,0,1,0,0,0,1,0,1,0]
                }


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
    def initialize(self, fans, active_fans, pattern_builder):
        self.fans = fans
        self.active_fans = active_fans
        self.pattern_builder = pattern_builder
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
        self._sendStats()
        self._stats_updater = task.LoopingCall(self._sendStats)
        self._stats_updater.start(5)

    def command_activate(self, message):
        self.fans[self].active = True
        self.active_fans[self] = self.fans[self]

        if len(self.active_fans) == 1:
            self.pattern_builder.start()

    def command_deactivate(self, message):
        if self in self.fans:
            self.fans[self].active = False
        if self in self.active_fans:
            del self.active_fans[self]

    def command_timesync(self, message):
        data = {
            'type': 'timesync',
            'data': {
                'sent_time' : message['data']['sent_time'],
                'server_time' : timestamp()
            }
        }
        self.sendMessage(cyclone.escape.json_encode(data))

    def connectionMade(self):
        self.fans[self] = FunMobile()

    def connectionLost(self, reason):
        if self in self.fans:
            del self.fans[self]

        if self in self.active_fans:
            del self.active_fans[self]

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
    
