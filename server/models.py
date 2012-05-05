from google.appengine.ext import db

class Tweet(db.Model):
    id = db.IntegerProperty()
    text = db.StringProperty(multiline=True)
    from_user_id = db.StringProperty()
    from_user = db.StringProperty()
    from_user_name = db.StringProperty(multiline=True)
    created_at = db.StringProperty(multiline=True)
    user_img = db.StringProperty()
    user_img_resized = db.BlobProperty()
    result_type = db.StringProperty()
    query_id = db.IntegerProperty()
