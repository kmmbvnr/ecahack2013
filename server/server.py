#!/usr/bin/env python2.7
import os
import sys
import cyclone.web
import cyclone.websocket
import cyclone.escape
from twisted.python import log
from twisted.internet import reactor


BASE_PATH = os.path.dirname(__file__)


class Application(cyclone.web.Application):
    def __init__(self):
        fans = []
        
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
    Mobile phone registration
    """
    def initialize(self, fans):
        self.fans = fans

    def messageReceived(self, message):
        parsed = cyclone.escape.json_decode(message)
        command = parsed['command']
        print('Got command %s' % command)


def main():
    reactor.listenTCP(9000, Application())
    reactor.run()


if __name__ == "__main__":
    log.startLogging(sys.stdout)
    main()
    
