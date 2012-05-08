from google.appengine.ext import db
from google.appengine.ext import blobstore

class Book(db.Model):
    title = db.StringProperty(multiline = True)
    owner = db.UserProperty()
    id = db.StringProperty()
    size = db.IntegerProperty()
    modified = db.DateTimeProperty(auto_now = True)
    created = db.DateTimeProperty()
    place = db.IntegerProperty()

class BookData(db.Model):
    id = db.StringProperty()
    start = db.IntegerProperty()
    end = db.IntegerProperty()
    data = db.BlobProperty()
    modified = db.DateTimeProperty(auto_now = True)
