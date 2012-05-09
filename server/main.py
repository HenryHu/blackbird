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

class MainPage(webapp.RequestHandler):
    def get(self):
        time = datetime.datetime.now()
        self.response.headers['Content-Type'] = 'text/html'
        self.response.out.write('''
                                <html>
                                <head>
                                    <title>Twitter searcher</title>
                                </head>
                                <body>
                                        Current time: %s<br/>
                                <div>
                                Search Twitter:
                                    <form action='/query' method="post" id="search-form">
                                        <input id="query_text" name="q" type="text"></input>
                                        <input value="Search" type="submit"></input>
                                    </form>
                                </div>
                                </body>
                                </html>
                                ''' % (str(time)))

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
                self.response.out.write('{"result":"ok"}')
        except ReportedError:
            return
    
class QueryPage(webapp.RequestHandler):
    def post(self):
        try:
            queryid = random.randint(0, 100000000)
            query = self.request.get("q")
            self.response.out.write("<h3>your request: <b>%s</b><br/>" % query)
            self.response.out.write("Results: <br/></h3>")

            # Query Twitter for search results

            #self.response.out.write("Getting...<br/>")
            data = urllib2.urlopen(search_url % urllib.urlencode({"q": query})).read()
#            self.response.out.write(data)

            # parse JSON data, store into DataStore

            #self.response.out.write("Loading...<br/>")
            result = json.loads(data)
            if "error" in result:
                self.response.out.write("Error: %s<br/>" % results["error"])
                return

            tweets = []
            for tweet in result["results"]:
                #self.response.out.write("posted: %s<br/>" % tweet["created_at"])
                #self.response.out.write("image: %s<br/>" % tweet["profile_image_url"])
                #self.response.out.write("from: %s(%s)[%s]<br/>" % (tweet["from_user_id_str"], tweet["from_user"], tweet["from_user_name"]))
                #self.response.out.write("text: %s<br/>" % tweet["text"])
                #self.response.out.write("id: %d<br/>" % tweet["id"])

                # get profile image, and resize it
                img_data = urllib2.urlopen(tweet["profile_image_url"]).read()
                #print "img data len: %d\n" % len(img_data)
                resized_img = resize(img_data, 100, 100)
                #print "resize data len: %d\n" % len(resized_img)

                tweetobj = Tweet(id = tweet["id"], text = tweet["text"],
                    from_user_id = tweet["from_user_id_str"],
                    from_user = tweet["from_user"],
                    from_user_name = tweet["from_user_name"],
                    created_at = tweet["created_at"],
                    user_img = tweet["profile_image_url"],
                    user_img_resized = resized_img,
                    result_type = tweet["metadata"]["result_type"],
                    query_id = queryid)
                tweetobj.put()
                tweets += [tweetobj.id]

            # store 10 recent tweets into memcache

            recent_tweets = Tweet.all()
            recent_tweets.filter('query_id =', queryid)
            recent_tweets.filter('result_type =', 'recent')
            recent_tweets = recent_tweets.fetch(10)
            counter = 0
            for tweet in recent_tweets:
                memcache.add("recent%d_id" % counter, str(tweet.id), 10)
                memcache.add("recent%d_username" % counter, tweet.from_user_name, 10)
                memcache.add("recent%d_user" % counter, tweet.from_user, 10)
                memcache.add("recent%d" % counter, tweet.text, 10)
                counter += 1

            # write results on the page

            self.response.out.write('''<table>
            <tr><th>Image</th><th>Username</th><th>Tweet</th>
                <th>Create time</th></tr>''')

            # load tweets from DataStore
            show_tweets = Tweet.all()
            show_tweets.filter('query_id =', queryid)
            for tweet in show_tweets:
                self.response.out.write('''<tr><td><img src="%s"/></td><td>%s(@%s)</td>
                      <td>%s</td><td>%s</td></tr>''' % ("/img/%d" % tweet.id,
                      tweet.from_user_name, tweet.from_user, tweet.text, tweet.created_at))
            self.response.out.write('</table>')

        except Exception as e:
            self.response.out.write('<br/>invalid request<br/>')
            self.response.out.write('Exception: %r<br/>' % e)
            self.response.out.write(traceback.format_exc().replace('\n', '<br/>'))

application = webapp.WSGIApplication(
    [('/', MainPage), ('/query', QueryPage), ('/book/(.*)', BookReq)], debug = True)

def main():
    run_wsgi_app(application)

if __name__ == '__main__':
    main()
