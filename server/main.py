from google.appengine.ext import webapp
from google.appengine.ext import blobstore
from google.appengine.ext.webapp.util import run_wsgi_app
from google.appengine.api.images import resize
from google.appengine.api import memcache
from google.appengine.api import mail
from google.appengine.api import users
from google.appengine.api import files
import base64
import traceback
import json
import datetime
from models import *
import urllib
import urllib2
import random
import json
import string
import datetime
import logging

random_chars = string.ascii_lowercase + string.digits

class ReportedError(Exception):
    pass

class MyRequestHandler(webapp.RequestHandler):
    def report_error(self, msg):
        self.response.out.write('{"error":"%s"}' % msg)

    def err_forbidden(self):
        self.report_error("forbidden")

    def get_strarg(self, argname):
        if (self.request.get(argname) == ""):
            self.report_error("missing argument %s" % argname)
            raise ReportedError("missing argument %s" % argname)
        return self.request.get(argname)

    def get_intarg(self, argname):
        argval = self.get_strarg(argname)
        try:
            iargval = int(argval)
        except ValueError:
            self.report_error("invalid format for arg %s" % argname)
            raise ReportedError("invalid format for arg %s" % argname)
        return iargval

def get_random_str(length = 16):
    return ''.join(random.choice(random_chars) for x in range(length))

def find_book(id):
    books = Book.all()
    books.filter('id = ', id)
    for book in books:
        return book
    return None

class BookReq(MyRequestHandler):
    def find_book(self, id):
        books = Book.all().filter('id = ', id)
        for book in books:
            return book
        self.report_error("no such book")
        raise ReportedError()

    def find_my_book(self, id):
        book = self.find_book(id)
        if (book.owner != users.get_current_user()):
            self.err_forbidden()
            raise ReportedError()
        return book

    def get(self, op):
        try:
            user = users.get_current_user()
            if (op == "list"):
                books = Book.all()
                books.filter('owner = ', user)
                self.response.out.write('[')
                first = True
                for book in books:
                    if not first:
                        self.response.out.write(',')
                    first = False
                    ret_book = {}
                    ret_book["title"] = book.title
                    ret_book["size"] = book.size
                    ret_book["id"] = book.id
                    ret_book["owner"] = book.owner.user_id()
                    ret_book["place"] = book.place
                    self.response.out.write(json.dumps(ret_book))
                self.response.out.write(']')
            elif (op == "get"):
                bookid = self.get_strarg("id")
                start = self.get_intarg("start")
                end = self.get_intarg("end")
                book = self.find_my_book(bookid)
                if (end > book.size):
                    end = book.size
                self.response.headers['Content-Type'] = 'application/octet-stream'
                blocks = BookData.all().filter('id = ', bookid)
                blocks.filter('start < ', end)
                blocks.order('start')
                blocks.order('-modified')
                cur_pos = start
                ret = ""
                for block in blocks:
                    if (block.end > start):
                        if (block.start <= cur_pos and block.end > cur_pos):
                            cur_end = 0
                            if (end < block.end):
                                cur_end = end
                            else:
                                cur_end = block.end
                            ret += block.data[cur_pos - block.start : cur_end - block.start]
                            cur_pos = cur_end
                self.response.out.write(base64.b64encode(ret))
            elif (op == "where"):
                bookid = self.get_strarg("id")
                book = self.find_my_book(bookid)
                place = book.place
                self.response.out.write('{"place":%d}' % place)
        except ReportedError:
            return

    def post(self, op):
        try:
            user = users.get_current_user()
            if (op == "add"):
                book_title = self.get_strarg("title")
                book_size = self.get_intarg("size")

                bookid = get_random_str()
                while (find_book(bookid) != None):
                    bookid = get_random_str()
                newbook = Book(id = bookid,
                               title = book_title,
                               size = book_size,
                               owner = user,
                               created = datetime.datetime.now(),
                               place = 0)
                newbook.put()

                book_info = {}
                book_info["id"] = bookid
                self.response.out.write(json.dumps(book_info))
            elif (op == "put"):
                bookid = self.get_strarg("id")
                start = self.get_intarg("start")
                end = self.get_intarg("end")
                data = str(self.get_strarg("data"))

                book = self.find_my_book(bookid)
                if (end > book.size):
                    end = book.size
                bookdata = BookData(id = bookid,
                                    start = start,
                                    end = end,
                                    data = base64.b64decode(data))
                bookdata.put()
                self.response.out.write('{"result":"ok"}')
            elif (op == "del"):
                bookid = self.request.get("id")
                book = self.find_my_book(bookid)
                book.delete()
                for block in BookData.all().filter('id = ', bookid):
                    block.delete()

                self.response.out.write('{"result":"ok"}')
            elif (op == "here"):
                bookid = self.get_strarg("id")
                place = self.get_intarg("place")
                book = self.find_my_book(bookid)
                book.place = place
                book.put()
                logger = logging.getLogger("BookReq")
                logger.debug("saving book place id: %s place: %d", bookid, place)
                self.response.out.write('{"result":"ok"}')
        except ReportedError:
            return

class MainPage(MyRequestHandler):
    def get(self):
        self.response.out.write('<html><head><title>Welcome</title></head><body><h1><center>Please use clients to access this service.</center></h1></body></html>')

application = webapp.WSGIApplication(
    [('/', MainPage), ('/book/(.*)', BookReq)], debug = True)

def main():
    run_wsgi_app(application)

if __name__ == '__main__':
    main()
