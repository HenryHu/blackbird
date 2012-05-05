from google.appengine.ext import webapp
from google.appengine.ext.webapp.util import run_wsgi_app
from google.appengine.api.images import resize
from google.appengine.api import memcache
from google.appengine.api import mail
import traceback
import json
import datetime
from models import Tweet
import urllib
import urllib2
import random

search_url = "http://search.twitter.com/search.json?%s"

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

class BookReq(webapp.RequestHandler):
    def get(self, op):
        if (op == "list"):
            self.response.out.write('[{"title": "book title 1", "size" : 1024}]')

class ImageReq(webapp.RequestHandler):
    def get(self, id):
        self.response.headers['Content-Type'] = 'image/png'
        tweets = Tweet.all()
        tweets.filter('id =', int(id))
        tweets = tweets.fetch(1)
        for tweet in tweets:
            self.response.out.write(tweet.user_img_resized)
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
    [('/', MainPage), ('/query', QueryPage), ('/img/(.*)', ImageReq), ('/book/(.*)', BookReq)], debug = True)

def main():
    run_wsgi_app(application)

if __name__ == '__main__':
    main()
