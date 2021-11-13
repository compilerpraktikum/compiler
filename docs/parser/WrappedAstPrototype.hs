{-# LANGUAGE ExplicitForAll #-}
{-# LANGUAGE LambdaCase #-}
{-# LANGUAGE RankNTypes #-}

data Stmt f g
  = IfStmt (f (Expr f)) (g (Stmt f g))
  | Return (f (Expr f))

data Expr f
  = Literal Int
  | Unary String (f (Expr f))

--- Kind<f, Expression<f>>

map' :: (f (Expr f) -> g (Expr g)) -> Expr f -> Expr g
map' alg = \case
  Literal a -> Literal a
  Unary s v -> Unary s (alg v)

newtype ConstValue a = ConstValue {unValue :: Int}

exec :: Identity (Expr Identity) -> ConstValue (Expr ConstValue)
exec = \case
  Identity (Literal a) -> ConstValue a
  Identity (Unary s v) -> ConstValue (unValue $ exec v)

class Functor1 t where
  map1 :: forall g f. Functor g => (forall a. f a -> g a) -> t f -> t g

instance Functor1 Expr where
  -- map1 :: (forall a. f a -> g a) -> Expr f -> Expr g
  map1 f = \case
    Literal i -> Literal i
    Unary s c ->
      Unary s $ fmap (map1 f) (f c)

normalToLenientValid :: Expr Identity -> Expr Lenient
normalToLenientValid = map1 $ \(Identity a) -> Valid a

newtype Identity a = Identity a deriving (Show)

normalAST :: Expr Identity
normalAST = Unary "!" (Identity (Literal 2))

normalAstAsValid :: Expr Lenient
normalAstAsValid = normalToLenientValid normalAST

data Lenient a = Error String | Valid a deriving (Show)

lenientAST :: Expr Lenient
lenientAST = Unary "!" (Valid (Unary "^" (Error "invalid")))

lenientStmt :: Stmt Lenient Lenient
lenientStmt = IfStmt (Valid lenientAST) (Valid (Return (Valid lenientAST)))

instance Functor Lenient where
  fmap f = \case
    Error s -> Error s
    Valid a -> Valid $ f a

data Annotated a = Annotated {ty :: String, content :: a}

instance Functor Annotated where
  fmap f (Annotated ty content) = Annotated ty $ f content

newtype Apply f g a = Apply (f (g a))

exampleNested :: Apply Lenient Annotated (Expr (Apply Lenient Annotated))
exampleNested = Apply $ Valid $ Annotated "Int" $ Unary "-" $ Apply $ Valid $ Annotated "Int" $ Literal 2